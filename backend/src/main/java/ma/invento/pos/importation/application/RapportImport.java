package ma.invento.pos.importation.application;

import java.util.List;
import ma.invento.pos.importation.domain.FormatImport;
import ma.invento.pos.importation.domain.InventaireDisponible;

/**
 * Bilan d'un import (ou de son analyse, sans écriture). Les listes de messages sont bornées ;
 * les compteurs restent exacts.
 */
public record RapportImport(
        FormatImport format,
        List<InventaireDisponible> inventaires,
        InventaireDisponible inventaireRetenu,
        int lignesLues,
        int produitsCrees,
        int produitsMisAJour,
        int mouvementsStock,
        int produitsEnStockNegatif,
        int produitsSansCodeBarre,
        int nombreErreurs,
        List<String> erreurs,
        int nombreAvertissements,
        List<String> avertissements) {

    static final int MESSAGES_MAX = 100;

    static List<String> borner(List<String> messages) {
        return messages.size() <= MESSAGES_MAX ? messages : List.copyOf(messages.subList(0, MESSAGES_MAX));
    }
}
