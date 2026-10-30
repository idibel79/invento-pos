package ma.invento.pos.identite.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.Utilisateur;

/** Contrats de l'API utilisateurs et authentification. Aucun hash n'en sort jamais. */
public final class UtilisateurDtos {

    private UtilisateurDtos() {}

    public record UtilisateurDto(
            Long id,
            String nom,
            String prenom,
            String login,
            Role role,
            boolean actif,
            boolean verrouille,
            boolean doitChangerMotDePasse,
            boolean pinDefini,
            Set<Long> caisseIds,
            @Schema(nullable = true) Instant derniereConnexion,
            long version) {

        public static UtilisateurDto depuis(Utilisateur u) {
            return new UtilisateurDto(
                    u.getId(),
                    u.getNom(),
                    u.getPrenom(),
                    u.getLogin(),
                    u.getRole(),
                    u.isActif(),
                    u.isVerrouille(),
                    u.isDoitChangerMotDePasse(),
                    u.isPinDefini(),
                    u.getCaisseIds(),
                    u.getDerniereConnexion(),
                    u.getVersion());
        }
    }

    public record CreationUtilisateur(
            @NotBlank @Size(max = 80) String nom,
            @NotNull @Size(max = 80) String prenom,

            @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9._-]+")
            String login,

            @NotNull Role role,
            @NotBlank @Size(max = 72) String motDePasseTemporaire,
            @NotNull Set<Long> caisseIds) {}

    public record ModificationUtilisateur(
            @NotBlank @Size(max = 80) String nom,
            @NotNull @Size(max = 80) String prenom,
            @NotNull Role role,
            boolean actif,
            @NotNull Set<Long> caisseIds,
            long version) {}

    public record MotDePasseTemporaire(
            @NotBlank @Size(max = 72) String motDePasseTemporaire) {}

    public record PinCommande(
            @NotBlank @Pattern(regexp = "\\d{4}") String pin) {}

    public record Connexion(
            @NotBlank @Size(max = 60) String login,
            @NotBlank @Size(max = 72) String motDePasse) {}

    public record ChangementMotDePasse(
            @NotBlank @Size(max = 72) String motDePasseActuel,
            @NotBlank @Size(max = 72) String nouveauMotDePasse) {}

    public record RecuperationCommande(
            @NotBlank @Size(max = 40) String codeSecours,
            @NotBlank @Size(max = 72) String nouveauMotDePasse) {}

    /** Réponse de connexion ; le jeton de rafraîchissement part dans un cookie HttpOnly. */
    public record SessionDto(String accessToken, long expiresIn, UtilisateurDto utilisateur) {}

    public record RecuperationDto(String login, String nouveauCodeSecours) {}

    public record CodeSecoursDto(String codeSecours) {}
}
