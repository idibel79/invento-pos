package ma.invento.pos.importation.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import ma.invento.pos.importation.domain.ReparationEncodage;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

/**
 * Lit la première feuille d'un classeur Excel (.xlsx ou .xls) en texte brut, encodage réparé.
 * Les nombres sont rendus sans notation scientifique (codes-barres à 13 chiffres) et les dates
 * au format ISO.
 */
@Component
public class LecteurExcel {

    /** Garde-fou contre un fichier démesuré (le catalogue Sobrus compte ~2 000 lignes). */
    static final int LIGNES_MAX = 50_000;

    public List<List<String>> lirePremiereFeuille(InputStream contenu) {
        try (Workbook classeur = WorkbookFactory.create(contenu)) {
            if (classeur.getNumberOfSheets() == 0) return List.of();
            Sheet feuille = classeur.getSheetAt(0);
            if (feuille.getLastRowNum() >= LIGNES_MAX) {
                throw new RegleMetierException("Fichier trop volumineux (plus de " + LIGNES_MAX + " lignes).");
            }
            List<List<String>> lignes = new ArrayList<>(feuille.getLastRowNum() + 1);
            for (int i = 0; i <= feuille.getLastRowNum(); i++) {
                lignes.add(lire(feuille.getRow(i)));
            }
            return lignes;
        } catch (IOException | EncryptedDocumentException | IllegalArgumentException e) {
            throw new RegleMetierException("Fichier Excel illisible.");
        }
    }

    private static List<String> lire(Row ligne) {
        if (ligne == null || ligne.getLastCellNum() < 0) return List.of();
        List<String> cellules = new ArrayList<>(ligne.getLastCellNum());
        for (int i = 0; i < ligne.getLastCellNum(); i++) {
            cellules.add(texte(ligne.getCell(i)));
        }
        return cellules;
    }

    private static String texte(Cell cellule) {
        if (cellule == null) return "";
        CellType type = cellule.getCellType() == CellType.FORMULA
                ? cellule.getCachedFormulaResultType()
                : cellule.getCellType();
        return switch (type) {
            case STRING -> ReparationEncodage.reparer(cellule.getStringCellValue());
            case NUMERIC ->
                DateUtil.isCellDateFormatted(cellule)
                        ? cellule.getLocalDateTimeCellValue().toLocalDate().toString()
                        : nombre(cellule.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cellule.getBooleanCellValue());
            default -> "";
        };
    }

    /** 6118000050643.0 -> "6118000050643", 25.2 -> "25.2". */
    private static String nombre(double valeur) {
        return BigDecimal.valueOf(valeur).stripTrailingZeros().toPlainString();
    }
}
