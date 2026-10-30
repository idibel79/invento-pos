package ma.invento.pos.identite.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import ma.invento.pos.identite.config.AdminProperties;
import ma.invento.pos.identite.domain.GenerateurSecrets;
import ma.invento.pos.identite.domain.PolitiqueSecrets;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.identite.infrastructure.RefreshTokenRepository;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Création de l'admin unique à l'installation et réinitialisation par commande serveur. */
@Service
public class AdministrationAdminService {

    public static final String LOGIN_ADMIN = "admin";

    private final UtilisateurRepository utilisateurs;
    private final RefreshTokenRepository refreshTokens;
    private final CodeSecoursService codesSecours;
    private final GenerateurSecrets generateur;
    private final PasswordEncoder encoder;
    private final JournalService journal;
    private final AdminProperties properties;
    private final Clock clock;

    public AdministrationAdminService(
            UtilisateurRepository utilisateurs,
            RefreshTokenRepository refreshTokens,
            CodeSecoursService codesSecours,
            GenerateurSecrets generateur,
            PasswordEncoder encoder,
            JournalService journal,
            AdminProperties properties,
            Clock clock) {
        this.utilisateurs = utilisateurs;
        this.refreshTokens = refreshTokens;
        this.codesSecours = codesSecours;
        this.generateur = generateur;
        this.encoder = encoder;
        this.journal = journal;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Crée l'admin s'il n'existe pas encore. Renvoie les secrets à afficher une seule fois, ou
     * rien si l'installation est déjà faite.
     */
    @Transactional
    public Optional<SecretsInstallation> installerSiNecessaire() {
        if (utilisateurs.existsByRole(Role.ADMIN)) return Optional.empty();
        String motDePasse = properties.motDePasseInitial() == null
                        || properties.motDePasseInitial().isBlank()
                ? generateur.motDePasseTemporaire()
                : properties.motDePasseInitial();
        PolitiqueSecrets.verifierMotDePasse(LOGIN_ADMIN, motDePasse);
        Utilisateur admin = utilisateurs.save(new Utilisateur(
                "Administrateur", "", LOGIN_ADMIN, Role.ADMIN, encoder.encode(motDePasse), Instant.now(clock)));
        String code = codesSecours.renouveler();
        journal.enregistrer(null, ActionJournal.INSTALLATION_ADMIN, "Compte admin créé, code de secours généré");
        return Optional.of(new SecretsInstallation(admin.getLogin(), motDePasse, code));
    }

    /** Commande {@code --reset-admin} : mot de passe temporaire, compte réactivé et déverrouillé. */
    @Transactional
    public SecretsReinitialisation reinitialiserAdmin() {
        Utilisateur admin = utilisateurs
                .findByRole(Role.ADMIN)
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun compte admin : démarrez l'application."));
        String motDePasse = generateur.motDePasseTemporaire();
        admin.reinitialiserMotDePasse(encoder.encode(motDePasse));
        admin.reactiver();
        refreshTokens.revoquerTous(admin.getId());
        journal.enregistrer(
                null, ActionJournal.REINITIALISATION_ADMIN_COMMANDE, "Mot de passe admin temporaire défini");
        return new SecretsReinitialisation(admin.getLogin(), motDePasse);
    }

    public record SecretsInstallation(String login, String motDePasseTemporaire, String codeSecours) {}

    public record SecretsReinitialisation(String login, String motDePasseTemporaire) {}
}
