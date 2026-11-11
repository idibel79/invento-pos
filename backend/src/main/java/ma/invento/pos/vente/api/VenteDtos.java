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

    /** @param jetonAutorisation jeton du PIN qui autorise la remise de cet article (un par article) */
    public record LigneCommande(
            @NotNull Long produitId,
            @Min(1) @Max(9999) int quantite,
            @Valid RemiseDto remise,
            @Size(max = 2000) String jetonAutorisation) {}

    /**
     * Panier envoyé par la caisse : produits, quantités et remises, jamais de prix.
     *
     * @param jetonAutorisation jeton renvoyé par {@code POST /autorisations/remise} pour la remise
     *     ticket, si le vendeur ne peut pas l'accorder lui-même
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

    /**
     * Demande de validation.
     *
     * @param mode espèces si absent
     * @param especesRecues montant remis par le client, obligatoire en espèces ; avec le mode {@code CARTE},
     *     s'il est donné, paiement mixte : la part payée en espèces (sans monnaie), le reste par carte
     */
    public record ValidationCommande(
            @NotNull @Valid PanierCommande panier,
            @Schema(nullable = true) ModePaiement mode,

            @Schema(nullable = true) @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
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
     * @param autorisationRequise niveau le plus exigeant parmi les remises
     * @param autorisationAcquise chaque remise est accordée (vendeur, jeton ou vente reprise)
     * @param remises autorisation de chaque remise : une par article remisé, et le ticket
     */
    public record PanierDto(
            List<LigneDto> lignes,
            TotauxDto totaux,
            BigDecimal tauxRemiseMax,
            NiveauAutorisation autorisationRequise,
            boolean autorisationAcquise,
            boolean stockSuffisant,
            List<AccordRemiseDto> remises) {}

    /**
     * @param produitId article remisé, ou null pour la remise ticket
     * @param tauxRemise remise en % (de la ligne, ou du ticket)
     * @param autorisationRequise AUCUNE si le vendeur l'accorde lui-même
     * @param valideePar qui l'a accordée, null tant qu'elle ne l'est pas
     */
    public record AccordRemiseDto(
            @Schema(nullable = true) Long produitId,
            BigDecimal tauxRemise,
            NiveauAutorisation autorisationRequise,
            @Schema(nullable = true) Long valideePar) {}

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
            @Schema(nullable = true) PaiementDto paiement,

            @Schema(description = "Tous les paiements : espèces puis carte pour un paiement mixte")
            List<PaiementDto> paiements) {}
}
