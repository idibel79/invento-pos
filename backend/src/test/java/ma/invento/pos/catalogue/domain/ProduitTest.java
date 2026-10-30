package ma.invento.pos.catalogue.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProduitTest {

    private Produit produit() {
        Produit produit = new Produit("27000", Instant.EPOCH);
        produit.modifierFiche(
                new FicheProduit(
                        "DOLIPRANE",
                        new Categorie("Médicament"),
                        new Tva("Exonéré", BigDecimal.ZERO),
                        new Zone("Comptoir"),
                        new BigDecimal("9.60"),
                        new BigDecimal("6.34"),
                        null,
                        null,
                        null,
                        null,
                        true),
                Instant.EPOCH);
        return produit;
    }

    @Test
    void stock_importe_renvoie_la_variation() {
        Produit produit = produit();
        assertThat(produit.fixerStockImporte(5, Instant.EPOCH)).isEqualTo(5);
        assertThat(produit.fixerStockImporte(3, Instant.EPOCH)).isEqualTo(-2);
        assertThat(produit.fixerStockImporte(3, Instant.EPOCH)).isZero();
        assertThat(produit.getStock()).isEqualTo(3);
    }

    @Test
    void rupture_quand_le_stock_disponible_est_nul_ou_negatif() {
        Produit produit = produit();
        assertThat(produit.isEnRupture()).isTrue();
        produit.fixerStockImporte(2, Instant.EPOCH);
        assertThat(produit.getStockDisponible()).isEqualTo(2);
        assertThat(produit.isEnRupture()).isFalse();
        produit.fixerStockImporte(-4, Instant.EPOCH);
        assertThat(produit.isEnRupture()).isTrue();
    }

    @Test
    void remplacement_des_codes_barres_garde_les_codes_communs() {
        Produit produit = produit();
        produit.remplacerCodesBarres(List.of("111", "222"));
        produit.remplacerCodesBarres(List.of("222", "333", "333"));
        assertThat(produit.getCodesBarres()).containsExactly("222", "333");
        produit.remplacerCodesBarres(List.of());
        assertThat(produit.getCodesBarres()).isEmpty();
    }
}
