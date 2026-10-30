package ma.invento.pos.vente.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import ma.invento.pos.vente.domain.ModePaiement;
import ma.invento.pos.vente.domain.NiveauAutorisation;
import ma.invento.pos.vente.domain.StatutVente;
import ma.invento.pos.vente.domain.TypeRemise;

/** Contrats de l'API de vente. Tous les montants sont calculés par le serveur. */
public final class VenteDtos {

    private VenteDtos() {}

    public record RemiseDto(
            @NotNull TypeRemise type,

            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
            BigDecimal valeur) {}

    public record LigneCommande(
            @NotNull Long produitId,
            @Min(1) @Max(9999) int quantite,
            @Valid RemiseDto remise) {}

    /**
     * Panier envoyé par la caisse : produits, quantités et remises, jamais de prix.
     *
     * @param jetonAutorisation jeton renvoyé par {@code POST /autorisations/remise}, si la remise
     *     dépasse le plafond du vendeur
     * @param venteEnAttenteId vente en attente reprise, dont ce panier prend la suite
     */
    public record PanierCommande(
            @NotEmpty @Size(max = 200) List<@Valid @NotNull LigneCommande> lignes,
            @Valid RemiseDto remiseTicket,
            @Size(max = 255) String motifRemise,
            @Size(max = 2000) String jetonAutorisation,
            Long venteEnAttenteId) {}

    public record MiseEnAttenteCommande(
            @NotNull @Valid PanierCommande panier,
            @Size(max = 255) String note) {}

    public record ValidationCommande(
            @NotNull @Valid PanierCommande panier,

            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
            BigDecimal especesRecues) {}

    public record LigneDto(
            Long produitId,
            String designation,
            int quantite,
            BigDecimal prixUnitaire,
            BigDecimal tauxTva,
            @Schema(nullable = true) RemiseDto remise,
            BigDecimal montantBrut,
            BigDecimal remiseMontant,
            BigDecimal montantNet,
            boolean stockSuffisant) {}

    public record VentilationTvaDto(BigDecimal taux, BigDecimal totalHt, BigDecimal totalTva, BigDecimal totalTtc) {}

    public record TotauxDto(
            BigDecimal sousTotal,
            BigDecimal remisesLignes,
            BigDecimal remiseTicket,
            BigDecimal totalRemises,
            BigDecimal totalHt,
            BigDecimal totalTva,
            BigDecimal totalTtc,
            List<VentilationTvaDto> tva) {}

    /**
     * Aperçu calculé du panier.
     *
     * @param autorisationRequise niveau exigé par la plus forte remise
     * @param autorisationAcquise le vendeur, son jeton ou la vente reprise couvre ce niveau
     */
    public record PanierDto(
            List<LigneDto> lignes,
            TotauxDto totaux,
            BigDecimal tauxRemiseMax,
            NiveauAutorisation autorisationRequise,
            boolean autorisationAcquise,
            boolean stockSuffisant) {}

    public record PaiementDto(
            ModePaiement mode,
            BigDecimal montant,
            @Schema(nullable = true) BigDecimal montantRecu,
            @Schema(nullable = true) BigDecimal rendu) {}

    public record VenteDto(
            Long id,
            String numero,
            StatutVente statut,
            Long caisseId,
            Long caissierId,
            List<LigneDto> lignes,
            @Schema(nullable = true) RemiseDto remiseTicket,
            TotauxDto totaux,
            @Schema(nullable = true) String motifRemise,
            @Schema(nullable = true) Long valideePar,
            @Schema(nullable = true) String noteAttente,
            @Schema(nullable = true) Instant attenteExpireLe,
            Instant creeLe,
            @Schema(nullable = true) Instant finaliseeLe,
            @Schema(nullable = true) PaiementDto paiement) {}
}
