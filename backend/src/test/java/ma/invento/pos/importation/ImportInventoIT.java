package ma.invento.pos.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import ma.invento.pos.IntegrationTest;
import ma.invento.pos.catalogue.domain.Produit;
import ma.invento.pos.catalogue.infrastructure.ProduitRepository;
import ma.invento.pos.stock.domain.MouvementStock;
import ma.invento.pos.stock.infrastructure.MouvementStockRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

/** Import d'un export Invento contenant deux inventaires (cadrage § 3). */
class ImportInventoIT extends IntegrationTest {

    private static final List<String> ENTETES = List.of(
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

    @Autowired
    ProduitRepository produits;

    @Autowired
    MouvementStockRepository mouvements;

    @Test
    void importe_l_inventaire_le_plus_recent_avec_les_quantites_comptees() throws Exception {
        String pidCompte = "INV" + UUID.randomUUID().toString().substring(0, 8);
        String pidNonCompte = pidCompte + "B";
        long ancien = Instant.parse("2026-09-01T08:00:00Z").toEpochMilli();
        long recent = Instant.parse("2026-10-25T08:00:00Z").toEpochMilli();
        byte[] classeur = classeur(List.of(
                ligne("INV-2026-01", ancien, pidCompte, 10, 9.0),
                ligne("INV-2026-02", recent, pidCompte, 10, 6.0),
                ligne("INV-2026-02", recent, pidNonCompte, 4, null)));
        String admin = jetonAdmin();

        mvc.perform(multipart("/api/v1/imports/catalogue/analyse")
                        .file(new MockMultipartFile("fichier", "export-invento.xlsx", null, classeur))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("INVENTO"))
                .andExpect(jsonPath("$.inventaires[0].numero").value("INV-2026-02"))
                .andExpect(jsonPath("$.inventaires[1].numero").value("INV-2026-01"))
                .andExpect(jsonPath("$.inventaireRetenu.numero").value("INV-2026-02"));

        mvc.perform(multipart("/api/v1/imports/catalogue")
                        .file(new MockMultipartFile("fichier", "export-invento.xlsx", null, classeur))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produitsCrees").value(2));

        assertThat(stock(pidCompte)).isEqualTo(6); // quantité réelle comptée
        assertThat(stock(pidNonCompte)).isEqualTo(4); // pas compté : stock théorique

        // Réimport de l'ancien inventaire choisi explicitement : mouvement IMPORT de -6 +9.
        mvc.perform(multipart("/api/v1/imports/catalogue")
                        .file(new MockMultipartFile("fichier", "export-invento.xlsx", null, classeur))
                        .param("inventaire", "INV-2026-01")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produitsMisAJour").value(1));
        assertThat(stock(pidCompte)).isEqualTo(9);
        Long id = produits.findAll().stream()
                .filter(p -> p.getPid().equals(pidCompte))
                .findFirst()
                .orElseThrow()
                .getId();
        assertThat(mouvements.findByProduitIdOrderByIdAsc(id))
                .extracting(MouvementStock::getQuantite)
                .containsExactly(6, 3);
    }

    private int stock(String pid) {
        return produits.findAll().stream()
                .filter(p -> p.getPid().equals(pid))
                .findFirst()
                .map(Produit::getStock)
                .orElseThrow();
    }

    private static List<Object> ligne(String numero, long date, String pid, double stock, Double reelle) {
        return Arrays.asList(
                numero,
                String.valueOf(date),
                "CLOTURE",
                pid,
                "PRODUIT " + pid,
                "Parapharmacie (33.330%)",
                "TVA (20.00%)",
                "Rayon B",
                stock,
                "",
                null,
                null,
                "",
                "",
                45.0,
                30.0,
                reelle,
                null,
                "NON");
    }

    private static byte[] classeur(List<List<Object>> lignes) throws IOException {
        try (XSSFWorkbook classeur = new XSSFWorkbook();
                ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Sheet feuille = classeur.createSheet("Inventaire");
            Row entetes = feuille.createRow(0);
            for (int i = 0; i < ENTETES.size(); i++) entetes.createCell(i).setCellValue(ENTETES.get(i));
            for (int r = 0; r < lignes.size(); r++) {
                Row ligne = feuille.createRow(r + 1);
                List<Object> valeurs = lignes.get(r);
                for (int i = 0; i < valeurs.size(); i++) {
                    Object valeur = valeurs.get(i);
                    if (valeur instanceof Number nombre) ligne.createCell(i).setCellValue(nombre.doubleValue());
                    else if (valeur != null) ligne.createCell(i).setCellValue(valeur.toString());
                }
            }
            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }
}
