package ma.invento.pos.identite.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.SeuilsRemise;
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
            SeuilsRemiseDto seuilsRemise,
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
                    SeuilsRemiseDto.depuis(u.getSeuilsRemise()),
                    u.getDerniereConnexion(),
                    u.getVersion());
        }
    }

    /** PIN et seuils de remise : pour un responsable (ignorés pour un caissier, l'admin n'a pas de limite). */
    public record CreationUtilisateur(
            @NotBlank @Size(max = 80) String nom,
            @NotNull @Size(max = 80) String prenom,

            @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9._-]+")
            String login,

            @NotNull Role role,
            @NotBlank @Size(max = 72) String motDePasseTemporaire,
            @NotNull Set<Long> caisseIds,

            @Schema(nullable = true) @Pattern(regexp = "\\d{4}")
            String pin,

            @Valid @NotNull SeuilsRemiseDto seuilsRemise) {}

    /** Mot de passe temporaire et PIN facultatifs : absents (null), ils restent inchangés. */
    public record ModificationUtilisateur(
            @NotBlank @Size(max = 80) String nom,
            @NotNull @Size(max = 80) String prenom,

            @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9._-]+")
            String login,

            @Schema(nullable = true) @Size(max = 72) String motDePasseTemporaire,

            @Schema(nullable = true) @Pattern(regexp = "\\d{4}")
            String pin,

            @NotNull Role role,
            boolean actif,
            @NotNull Set<Long> caisseIds,
            @Valid @NotNull SeuilsRemiseDto seuilsRemise,
            long version) {}

    /** Remise maximale, en %, qu'un responsable peut accorder sur une ligne et sur le ticket. */
    public record SeuilsRemiseDto(
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
            BigDecimal ligne,

            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
            BigDecimal ticket) {

        public static SeuilsRemiseDto depuis(SeuilsRemise seuils) {
            return new SeuilsRemiseDto(seuils.ligne(), seuils.ticket());
        }

        public SeuilsRemise versDomaine() {
            return new SeuilsRemise(ligne, ticket);
        }
    }

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
