package ma.invento.pos.importation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.junit.jupiter.api.Test;

class AnalyseurFichierTest {

    private static final List<String> ENTETES_SOBRUS = List.of(
            "PID",
            "Produit",
            "Catégorie",
            "TVA",
            "PPV",
            "PPH",
            "Zone",
            "Stock",
            "Date de péremption",
            "Stock min",
            "Stock max",
            "Code barre 1",
            "Code barre 2");

    private static final List<String> ENTETES_INVENTO = List.of(
            "numero_inventaire",
            "date_inventaire",
            "statut",
            "pid",
            "produit",
            "categorie",
            "tva",
            "zone",
            "stock",
            "date_de_peremption",
            "stock_min",
            "stock_max",
            "code_barre_1",
            "code_barre_2",
            "ppv",
            "pph",
            "quantite_reelle",
            "ecart",
            "ecart_valide");

    private static List<String> sobrus(String pid, String codeBarre1, String codeBarre2) {
        return List.of(
                pid,
                "PRODUIT " + pid,
                "Médicament (33.930%)",
                "Exonéré (0.00%)",
                "10.00",
                "6.60",
                "Comptoir",
                "3",
                "",
                "",
                "",
                codeBarre1,
                codeBarre2);
    }

    private static List<String> invento(String numero, long date, String pid, String stock, String reelle) {
        return List.of(
                numero,
                String.valueOf(date),
                "CLOTURE",
                pid,
                "PRODUIT " + pid,
                "Médicament (33.930%)",
                "TVA (20.00%)",
                "Comptoir",
                stock,
                "",
                "",
                "",
                "",
                "",
                "12.5",
                "8",
                reelle,
                "",
                "NON");
    }

    @Test
    void detecte_le_format_sobrus_et_lit_les_lignes() {
        FichierCatalogue fichier = AnalyseurFichier.analyser(
                List.of(ENTETES_SOBRUS, sobrus("1", "111", ""), sobrus("2", "222", "223")), null);

        assertThat(fichier.format()).isEqualTo(FormatImport.SOBRUS);
        assertThat(fichier.inventaireRetenu()).isNull();
        assertThat(fichier.lignes()).extracting(LigneCatalogue::pid).containsExactly("1", "2");
        assertThat(fichier.erreurs()).isEmpty();
    }

    @Test
    void ignore_les_lignes_vides_et_les_pid_en_double() {
        FichierCatalogue fichier = AnalyseurFichier.analyser(
                List.of(ENTETES_SOBRUS, sobrus("1", "", ""), List.of(), List.of("", ""), sobrus("1", "", "")), null);

        assertThat(fichier.lignes()).hasSize(1);
        assertThat(fichier.erreurs()).singleElement().asString().contains("Ligne 5", "PID 1");
    }

    @Test
    void code_barre_deja_vu_garde_par_la_premiere_ligne() {
        FichierCatalogue fichier = AnalyseurFichier.analyser(
                List.of(ENTETES_SOBRUS, sobrus("1", "111", ""), sobrus("2", "111", "222")), null);

        assertThat(fichier.lignes().get(1).codesBarres()).containsExactly("222");
        assertThat(fichier.avertissements()).singleElement().asString().contains("111");
    }

    @Test
    void ligne_invalide_signalee_sans_bloquer_les_autres() {
        List<String> invalide = new ArrayList<>(sobrus("2", "", ""));
        invalide.set(4, "abc");
        FichierCatalogue fichier =
                AnalyseurFichier.analyser(List.of(ENTETES_SOBRUS, sobrus("1", "", ""), invalide), null);

        assertThat(fichier.lignes()).hasSize(1);
        assertThat(fichier.erreurs()).singleElement().asString().contains("Ligne 3", "PPV");
    }

    @Test
    void refuse_un_fichier_sans_les_colonnes_attendues() {
        assertThatThrownBy(() -> AnalyseurFichier.analyser(List.of(List.of("Nom", "Prix")), null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("pid");
        assertThatThrownBy(() -> AnalyseurFichier.analyser(List.of(), null)).isInstanceOf(RegleMetierException.class);
    }

    @Test
    void invento_retient_l_inventaire_le_plus_recent_et_sa_quantite_reelle() {
        long ancien = Instant.parse("2026-09-01T10:00:00Z").toEpochMilli();
        long recent = Instant.parse("2026-10-20T10:00:00Z").toEpochMilli();
        FichierCatalogue fichier = AnalyseurFichier.analyser(
                List.of(
                        ENTETES_INVENTO,
                        invento("INV-1", ancien, "1", "5.0", "4.0"),
                        invento("INV-2", recent, "1", "5.0", "2.0"),
                        invento("INV-2", recent, "2", "8.0", "")),
                null);

        assertThat(fichier.format()).isEqualTo(FormatImport.INVENTO);
        assertThat(fichier.inventaires())
                .extracting(InventaireDisponible::numero)
                .containsExactly("INV-2", "INV-1");
        assertThat(fichier.inventaireRetenu().numero()).isEqualTo("INV-2");
        assertThat(fichier.inventaireRetenu().nombreArticles()).isEqualTo(2);
        assertThat(fichier.lignes()).extracting(LigneCatalogue::stock).containsExactly(2, 8);
        assertThat(fichier.lignes().getFirst().tvaTaux()).isEqualByComparingTo("20");
    }

    @Test
    void invento_importe_l_inventaire_choisi() {
        long ancien = Instant.parse("2026-09-01T10:00:00Z").toEpochMilli();
        long recent = Instant.parse("2026-10-20T10:00:00Z").toEpochMilli();
        List<List<String>> lignes = List.of(
                ENTETES_INVENTO, invento("INV-1", ancien, "1", "5", "4"), invento("INV-2", recent, "1", "5", "2"));

        assertThat(AnalyseurFichier.analyser(lignes, "INV-1")
                        .lignes()
                        .getFirst()
                        .stock())
                .isEqualTo(4);
        assertThatThrownBy(() -> AnalyseurFichier.analyser(lignes, "INV-9"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("INV-9");
    }
}
