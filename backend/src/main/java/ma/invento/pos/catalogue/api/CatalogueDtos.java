package ma.invento.pos.catalogue.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contrats de l'API catalogue. */
public final class CatalogueDtos {

    private CatalogueDtos() {}

    public record ReferenceDto(Long id, String nom) {}

    public record TvaDto(Long id, String libelle, BigDecimal taux) {}

    /**
     * Produit tel que vu par la caisse. {@code pph} (prix d'achat) et {@code tauxMarge} ne sont
     * renseignés que pour l'admin.
     */
    public record ProduitDto(
            Long id,
            String pid,
            String designation,
            ReferenceDto categorie,
            ReferenceDto zone,
            TvaDto tva,
            BigDecimal ppv,
            @Schema(nullable = true) BigDecimal pph,
            @Schema(nullable = true) BigDecimal tauxMarge,
            int stock,
            int stockReserve,
            int stockDisponible,
            boolean enRupture,
            @Schema(nullable = true) Integer stockMin,
            @Schema(nullable = true) Integer stockMax,
            @Schema(nullable = true) LocalDate datePeremption,
            boolean actif,
            List<String> codesBarres,
            long version) {}

    /** Création ou modification d'un produit par l'admin (le stock passe par des mouvements). */
    public record ProduitCommande(
            @NotBlank @Size(max = 40) String pid,
            @NotBlank @Size(max = 255) String designation,
            @NotNull Long categorieId,
            @NotNull Long tvaId,
            @NotNull Long zoneId,

            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
            BigDecimal ppv,

            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
            BigDecimal pph,

            @PositiveOrZero Integer stockMin,
            @PositiveOrZero Integer stockMax,
            LocalDate datePeremption,
            boolean actif,
            @NotNull @Size(max = 10) List<@NotBlank @Size(max = 64) String> codesBarres,
            /** Version lue avant modification (verrouillage optimiste) ; ignorée à la création. */
            Long version) {}

    public record ReferenceCommande(
            @NotBlank @Size(max = 120) String nom) {}
}
