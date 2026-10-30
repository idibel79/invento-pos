package ma.invento.pos.importation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LecteurLigneTest {

    private final List<String> avertissements = new ArrayList<>();

    private static Map<Colonne, String> ligneSobrus() {
        Map<Colonne, String> valeurs = new EnumMap<>(Colonne.class);
        valeurs.put(Colonne.PID, "27000");
        valeurs.put(Colonne.PRODUIT, "VIGOREX CO 50MG B1 COMP");
        valeurs.put(Colonne.CATEGORIE, "Médicament (33.930%)");
        valeurs.put(Colonne.TVA, "Exonéré (0.00%)");
        valeurs.put(Colonne.PPV, "25.20");
        valeurs.put(Colonne.PPH, "16.65");
        valeurs.put(Colonne.ZONE, "Comptoir ");
        valeurs.put(Colonne.STOCK, "4");
        valeurs.put(Colonne.CODE_BARRE_1, "6118000050643");
        return valeurs;
    }

    private LigneCatalogue lire(Map<Colonne, String> valeurs, FormatImport format) {
        return LecteurLigne.lire(2, valeurs, format, avertissements::add);
    }

    @Test
    void lit_une_ligne_sobrus() {
        LigneCatalogue ligne = lire(ligneSobrus(), FormatImport.SOBRUS);

        assertThat(ligne.pid()).isEqualTo("27000");
        assertThat(ligne.categorie()).isEqualTo("Médicament");
        assertThat(ligne.tauxMarge()).isEqualByComparingTo("33.930");
        assertThat(ligne.tvaLibelle()).isEqualTo("Exonéré");
        assertThat(ligne.tvaTaux()).isEqualByComparingTo("0");
        assertThat(ligne.zone()).isEqualTo("Comptoir");
        assertThat(ligne.ppv()).isEqualTo(new BigDecimal("25.20"));
        assertThat(ligne.pph()).isEqualTo(new BigDecimal("16.65"));
        assertThat(ligne.stock()).isEqualTo(4);
        assertThat(ligne.codesBarres()).containsExactly("6118000050643");
        assertThat(avertissements).isEmpty();
    }

    @Test
    void tva_a_20_pour_cent() {
        Map<Colonne, String> valeurs = ligneSobrus();
        valeurs.put(Colonne.TVA, "TVA (20.00%)");
        LigneCatalogue ligne = lire(valeurs, FormatImport.SOBRUS);
        assertThat(ligne.tvaLibelle()).isEqualTo("TVA");
        assertThat(ligne.tvaTaux()).isEqualByComparingTo("20");
    }

    @Test
    void tva_en_simple_nombre() {
        Map<Colonne, String> valeurs = ligneSobrus();
        valeurs.put(Colonne.TVA, "20");
        assertThat(lire(valeurs, FormatImport.SOBRUS).tvaTaux()).isEqualByComparingTo("20");
    }

    @Test
    void zone_vide_devient_non_assigne_et_espaces_reduits() {
        Map<Colonne, String> valeurs = ligneSobrus();
        valeurs.put(Colonne.ZONE, "  ");
        assertThat(lire(valeurs, FormatImport.SOBRUS).zone()).isEqualTo(LecteurLigne.ZONE_NON_ASSIGNEE);
        valeurs.put(Colonne.ZONE, "Rayon   bébé ");
        assertThat(lire(valeurs, FormatImport.SOBRUS).zone()).isEqualTo("Rayon bébé");
    }

    @Test
    void stock_vide_vaut_zero_et_stock_negatif_est_garde() {
        Map<Colonne, String> valeurs = ligneSobrus();
        valeurs.put(Colonne.STOCK, "");
        assertThat(lire(valeurs, FormatImport.SOBRUS).stock()).isZero();
        valeurs.put(Colonne.STOCK, "-3");
        assertThat(lire(valeurs, FormatImport.SOBRUS).stock()).isEqualTo(-3);
    }

    @Test
    void invento_prend_la_quantite_reelle_quand_l_article_a_ete_compte() {
        Map<Colonne, String> valeurs = ligneSobrus();
        valeurs.put(Colonne.STOCK, "4.0");
        valeurs.put(Colonne.QUANTITE_REELLE, "7.0");
        assertThat(lire(valeurs, FormatImport.INVENTO).stock()).isEqualTo(7);
        valeurs.put(Colonne.QUANTITE_REELLE, "");
        assertThat(lire(valeurs, FormatImport.INVENTO).stock()).isEqualTo(4);
    }

    @Test
    void sobrus_ignore_une_colonne_quantite_reelle() {
        Map<Colonne, String> valeurs = ligneSobrus();
        valeurs.put(Colonne.QUANTITE_REELLE, "9");
        assertThat(lire(valeurs, FormatImport.SOBRUS).stock()).isEqualTo(4);
    }

    @Test
    void prix_avec_virgule_arrondi_a_deux_decimales() {
        Map<Colonne, String> valeurs = ligneSobrus();
        valeurs.put(Colonne.PPV, "1 234,567");
        assertThat(lire(valeurs, FormatImport.SOBRUS).ppv()).isEqualTo(new BigDecimal("1234.57"));
    }

    @Test
    void codes_barres_vides_et_doublons_ignores() {
        Map<Colonne, String> valeurs = ligneSobrus();
        valeurs.put(Colonne.CODE_BARRE_1, "");
        valeurs.put(Colonne.CODE_BARRE_2, "123");
        assertThat(lire(valeurs, FormatImport.SOBRUS).codesBarres()).containsExactly("123");
        valeurs.put(Colonne.CODE_BARRE_1, "123");
        assertThat(lire(valeurs, FormatImport.SOBRUS).codesBarres()).containsExactly("123");
    }

    @Test
    void date_de_peremption_lisible_ou_ignoree_avec_avertissement() {
        Map<Colonne, String> valeurs = ligneSobrus();
        valeurs.put(Colonne.DATE_PEREMPTION, "31/12/2027");
        assertThat(lire(valeurs, FormatImport.SOBRUS).datePeremption()).isEqualTo(LocalDate.of(2027, 12, 31));
        valeurs.put(Colonne.DATE_PEREMPTION, "bientôt");
        assertThat(lire(valeurs, FormatImport.SOBRUS).datePeremption()).isNull();
        assertThat(avertissements).singleElement().asString().contains("péremption");
    }

    @Test
    void categorie_sans_taux_ou_vide() {
        Map<Colonne, String> valeurs = ligneSobrus();
        valeurs.put(Colonne.CATEGORIE, "Divers");
        LigneCatalogue ligne = lire(valeurs, FormatImport.SOBRUS);
        assertThat(ligne.categorie()).isEqualTo("Divers");
        assertThat(ligne.tauxMarge()).isNull();
        valeurs.put(Colonne.CATEGORIE, "");
        assertThat(lire(valeurs, FormatImport.SOBRUS).categorie()).isEqualTo(LecteurLigne.CATEGORIE_NON_CLASSEE);
    }

    @Test
    void refuse_les_donnees_obligatoires_manquantes_ou_illisibles() {
        Map<Colonne, String> sansPid = ligneSobrus();
        sansPid.put(Colonne.PID, " ");
        assertThatThrownBy(() -> lire(sansPid, FormatImport.SOBRUS)).isInstanceOf(LigneInvalideException.class);

        Map<Colonne, String> prixTexte = ligneSobrus();
        prixTexte.put(Colonne.PPV, "gratuit");
        assertThatThrownBy(() -> lire(prixTexte, FormatImport.SOBRUS)).hasMessageContaining("PPV illisible");

        Map<Colonne, String> prixNegatif = ligneSobrus();
        prixNegatif.put(Colonne.PPV, "-1");
        assertThatThrownBy(() -> lire(prixNegatif, FormatImport.SOBRUS)).hasMessageContaining("négatif");

        Map<Colonne, String> stockDecimal = ligneSobrus();
        stockDecimal.put(Colonne.STOCK, "2.5");
        assertThatThrownBy(() -> lire(stockDecimal, FormatImport.SOBRUS)).hasMessageContaining("non entier");

        Map<Colonne, String> sansTva = ligneSobrus();
        sansTva.put(Colonne.TVA, "");
        assertThatThrownBy(() -> lire(sansTva, FormatImport.SOBRUS)).hasMessageContaining("TVA manquante");

        Map<Colonne, String> tvaIllisible = ligneSobrus();
        tvaIllisible.put(Colonne.TVA, "normale");
        assertThatThrownBy(() -> lire(tvaIllisible, FormatImport.SOBRUS)).hasMessageContaining("TVA illisible");

        Map<Colonne, String> tvaTropHaute = ligneSobrus();
        tvaTropHaute.put(Colonne.TVA, "TVA (120%)");
        assertThatThrownBy(() -> lire(tvaTropHaute, FormatImport.SOBRUS)).hasMessageContaining("100 %");
    }
}
