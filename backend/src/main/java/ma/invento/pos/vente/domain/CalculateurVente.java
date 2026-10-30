package ma.invento.pos.vente.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import ma.invento.pos.vente.domain.PanierCalcule.LigneCalculee;
import ma.invento.pos.vente.domain.PanierCalcule.VentilationTva;

/**
 * Seul calcul des montants d'une vente (aperçu du panier, mise en attente et validation).
 *
 * <p>Les prix publics sont TTC. Chaque ligne peut porter une remise, puis la remise ticket
 * s'applique au total des lignes remisées. Pour ventiler la TVA, la remise ticket est répartie
 * sur les lignes au prorata de leur montant ; le HT se déduit du TTC par taux.
 */
public final class CalculateurVente {

    private CalculateurVente() {}

    public static PanierCalcule calculer(List<LignePanier> lignes, Remise remiseTicket) {
        List<LigneCalculee> calculees = new ArrayList<>();
        BigDecimal sousTotal = Montants.ZERO;
        BigDecimal remisesLignes = Montants.ZERO;
        BigDecimal tauxMax = Montants.ZERO;
        for (LignePanier ligne : lignes) {
            BigDecimal brut = Montants.arrondir(ligne.prixUnitaire().multiply(BigDecimal.valueOf(ligne.quantite())));
            BigDecimal remise =
                    ligne.remise() == null ? Montants.ZERO : ligne.remise().montantSur(brut);
            calculees.add(new LigneCalculee(ligne, brut, remise));
            sousTotal = sousTotal.add(brut);
            remisesLignes = remisesLignes.add(remise);
            tauxMax = tauxMax.max(Montants.pourcentage(remise, brut));
        }
        BigDecimal netLignes = sousTotal.subtract(remisesLignes);
        BigDecimal remiseTicketMontant = remiseTicket == null ? Montants.ZERO : remiseTicket.montantSur(netLignes);
        tauxMax = tauxMax.max(Montants.pourcentage(remiseTicketMontant, netLignes));

        List<VentilationTva> tva = ventiler(calculees, netLignes, remiseTicketMontant);
        BigDecimal totalHt = tva.stream().map(VentilationTva::totalHt).reduce(Montants.ZERO, BigDecimal::add);
        BigDecimal totalTva = tva.stream().map(VentilationTva::totalTva).reduce(Montants.ZERO, BigDecimal::add);
        return new PanierCalcule(
                List.copyOf(calculees),
                sousTotal,
                remisesLignes,
                remiseTicketMontant,
                totalHt,
                totalTva,
                netLignes.subtract(remiseTicketMontant),
                tva,
                tauxMax);
    }

    /** TTC par taux après répartition de la remise ticket (le reste d'arrondi va à la dernière ligne). */
    private static List<VentilationTva> ventiler(
            List<LigneCalculee> lignes, BigDecimal netLignes, BigDecimal remiseTicket) {
        Map<BigDecimal, BigDecimal> ttcParTaux = new TreeMap<>();
        BigDecimal resteARepartir = remiseTicket;
        for (int i = 0; i < lignes.size(); i++) {
            LigneCalculee ligne = lignes.get(i);
            BigDecimal part;
            if (i == lignes.size() - 1) {
                part = resteARepartir;
            } else if (netLignes.signum() == 0) {
                part = Montants.ZERO;
            } else {
                part = remiseTicket.multiply(ligne.montantNet()).divide(netLignes, 2, RoundingMode.HALF_UP);
            }
            resteARepartir = resteARepartir.subtract(part);
            BigDecimal taux = ligne.ligne().tauxTva().setScale(2, RoundingMode.HALF_UP);
            ttcParTaux.merge(taux, ligne.montantNet().subtract(part), BigDecimal::add);
        }
        List<VentilationTva> ventilation = new ArrayList<>();
        ttcParTaux.forEach((taux, ttc) -> {
            BigDecimal diviseur = BigDecimal.ONE.add(taux.divide(Montants.CENT));
            BigDecimal ht = ttc.divide(diviseur, 2, RoundingMode.HALF_UP);
            ventilation.add(new VentilationTva(taux, ht, ttc.subtract(ht), ttc));
        });
        return List.copyOf(ventilation);
    }
}
