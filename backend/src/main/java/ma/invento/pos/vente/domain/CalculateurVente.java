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
        for (LignePanier ligne : lignes) {
            BigDecimal brut = Montants.arrondir(ligne.prixUnitaire().multiply(BigDecimal.valueOf(ligne.quantite())));
            BigDecimal remise =
                    ligne.remise() == null ? Montants.ZERO : ligne.remise().montantSur(brut);
            calculees.add(new LigneCalculee(ligne, brut, remise));
            sousTotal = sousTotal.add(brut);
            remisesLignes = remisesLignes.add(remise);
        }
        BigDecimal netLignes = sousTotal.subtract(remisesLignes);
        BigDecimal remiseTicketMontant = remiseTicket == null ? Montants.ZERO : remiseTicket.montantSur(netLignes);

        List<BigDecimal> partsTicket = repartir(calculees, netLignes, remiseTicketMontant);
        // Remise effective de chaque ligne : la sienne plus sa part exacte (avant arrondi) de la
        // remise ticket, pour que cumuler remise ligne et remise ticket ne contourne pas les plafonds.
        BigDecimal partTicket = netLignes.signum() == 0
                ? BigDecimal.ZERO
                : remiseTicketMontant.divide(netLignes, 10, RoundingMode.HALF_UP);
        BigDecimal tauxMax = Montants.ZERO;
        for (LigneCalculee ligne : calculees) {
            BigDecimal remiseEffective =
                    ligne.remiseMontant().add(ligne.montantNet().multiply(partTicket));
            tauxMax = tauxMax.max(Montants.pourcentage(remiseEffective, ligne.montantBrut()));
        }

        List<VentilationTva> tva = ventiler(calculees, partsTicket);
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

    /**
     * Part de la remise ticket de chaque ligne, au prorata de son montant net ; le reste d'arrondi
     * va à la ligne au plus fort montant net, qui peut toujours l'absorber.
     */
    private static List<BigDecimal> repartir(
            List<LigneCalculee> lignes, BigDecimal netLignes, BigDecimal remiseTicket) {
        List<BigDecimal> parts = new ArrayList<>();
        BigDecimal reste = remiseTicket;
        int plusForte = 0;
        for (int i = 0; i < lignes.size(); i++) {
            BigDecimal net = lignes.get(i).montantNet();
            BigDecimal part = netLignes.signum() == 0
                    ? Montants.ZERO
                    : remiseTicket.multiply(net).divide(netLignes, 2, RoundingMode.HALF_UP);
            parts.add(part);
            reste = reste.subtract(part);
            if (net.compareTo(lignes.get(plusForte).montantNet()) > 0) plusForte = i;
        }
        if (!parts.isEmpty()) parts.set(plusForte, parts.get(plusForte).add(reste));
        return parts;
    }

    /** TTC par taux après déduction de la part de remise ticket de chaque ligne. */
    private static List<VentilationTva> ventiler(List<LigneCalculee> lignes, List<BigDecimal> partsTicket) {
        Map<BigDecimal, BigDecimal> ttcParTaux = new TreeMap<>();
        for (int i = 0; i < lignes.size(); i++) {
            LigneCalculee ligne = lignes.get(i);
            BigDecimal taux = ligne.ligne().tauxTva().setScale(2, RoundingMode.HALF_UP);
            ttcParTaux.merge(taux, ligne.montantNet().subtract(partsTicket.get(i)), BigDecimal::add);
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
