package ma.invento.pos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base des tests d'intégration : application complète sur un PostgreSQL Testcontainers partagé
 * (même contexte Spring pour toutes les classes). Chaque test crée ses propres données.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String MDP_INITIAL_ADMIN = "Installation-2026";
    protected static final String MDP_ADMIN = "Admin-Pos-2026";
    protected static final String MDP_UTILISATEUR = "Caisse-2026!";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected UtilisateurRepository utilisateurs;

    @Autowired
    protected PasswordEncoder encoder;

    /** Jeton de l'admin ; au premier appel, remplace le mot de passe d'installation. */
    protected String jetonAdmin() throws Exception {
        Utilisateur admin = utilisateurs.findByRole(Role.ADMIN).orElseThrow();
        if (admin.isDoitChangerMotDePasse()) {
            String temporaire = jeton(admin.getLogin(), MDP_INITIAL_ADMIN);
            mvc.perform(post("/api/v1/auth/mot-de-passe")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + temporaire)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(
                                    Map.of("motDePasseActuel", MDP_INITIAL_ADMIN, "nouveauMotDePasse", MDP_ADMIN))))
                    .andExpect(status().isOk());
        }
        return jeton(admin.getLogin(), MDP_ADMIN);
    }

    /** Crée directement en base un compte actif, mot de passe déjà choisi. */
    protected Utilisateur creerUtilisateur(Role role) {
        String login =
                role.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Utilisateur utilisateur =
                new Utilisateur("Test", "Utilisateur", login, role, encoder.encode(MDP_UTILISATEUR), Instant.now());
        utilisateur.changerMotDePasse(encoder.encode(MDP_UTILISATEUR));
        return utilisateurs.save(utilisateur);
    }

    protected String jeton(Role role) throws Exception {
        return jeton(creerUtilisateur(role).getLogin(), MDP_UTILISATEUR);
    }

    protected String jeton(String login, String motDePasse) throws Exception {
        MvcResult resultat =
                connexion(login, motDePasse).andExpect(status().isOk()).andReturn();
        return lire(resultat).get("accessToken").asText();
    }

    protected ResultActions connexion(String login, String motDePasse) throws Exception {
        return mvc.perform(post("/api/v1/auth/connexion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("login", login, "motDePasse", motDePasse))));
    }

    protected JsonNode lire(MvcResult resultat) throws Exception {
        return json.readTree(resultat.getResponse().getContentAsString());
    }

    protected static String bearer(String jeton) {
        return "Bearer " + jeton;
    }
}
