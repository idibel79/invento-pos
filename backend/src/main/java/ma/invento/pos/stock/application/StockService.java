package ma.invento.pos.stock.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import ma.invento.pos.catalogue.infrastructure.ProduitRepository;
import ma.invento.pos.shared.domain.RegleMetierException;
import ma.invento.pos.stock.domain.MouvementStock;
import ma.invento.pos.stock.domain.TypeMouvement;
import ma.invento.pos.stock.infrastructure.MouvementStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Variations de stock liées aux ventes, par mises à jour conditionnelles atomiques. Les produits
 * sont toujours traités par identifiant croissant pour que deux ventes concurrentes verrouillent
 * leurs lignes dans le même ordre (pas d'interblocage).
 */
@Service
public class StockService {

    private final ProduitRepository produits;
    private final MouvementStockRepository mouvements;

    public StockService(ProduitRepository produits, MouvementStockRepository mouvements) {
        this.produits = produits;
        this.mouvements = mouvements;
    }

    /** Réserve le stock d'une vente mise en attente ; tout ou rien. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserver(List<Quantite> quantites) {
        for (Quantite q : trier(quantites)) {
            if (produits.reserver(q.produitId(), q.quantite()) == 0) {
                throw stockInsuffisant(q);
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void liberer(List<Quantite> quantites) {
        for (Quantite q : trier(quantites)) {
            if (produits.liberer(q.produitId(), q.quantite()) == 0) {
                throw new IllegalStateException("Réserve de stock incohérente pour le produit " + q.produitId());
            }
        }
    }

    /** Sortie de stock d'une vente validée, avec un mouvement {@code VENTE} par produit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sortirPourVente(List<Quantite> quantites, String reference, long utilisateurId, Instant maintenant) {
        for (Quantite q : trier(quantites)) {
            if (produits.sortir(q.produitId(), q.quantite()) == 0) {
                throw stockInsuffisant(q);
            }
        }
        mouvements.saveAll(quantites.stream()
                .map(q -> new MouvementStock(
                        q.produitId(), TypeMouvement.VENTE, -q.quantite(), reference, maintenant, utilisateurId))
                .toList());
    }

    private static List<Quantite> trier(List<Quantite> quantites) {
        return quantites.stream()
                .sorted(Comparator.comparing(Quantite::produitId))
                .toList();
    }

    private static RegleMetierException stockInsuffisant(Quantite q) {
        return new RegleMetierException("Stock insuffisant pour " + q.designation() + ".");
    }

    /** Quantité d'un produit ; la désignation sert au message d'erreur. */
    public record Quantite(long produitId, String designation, int quantite) {}
}
