package ma.invento.pos.importation.domain;

import java.util.List;

/**
 * Résultat de la lecture d'un fichier, avant toute écriture en base.
 *
 * @param inventaires inventaires trouvés (export Invento uniquement), le plus récent en premier
 * @param inventaireRetenu inventaire importé, ou {@code null} pour un export Sobrus
 * @param erreurs lignes ignorées et pourquoi
 * @param avertissements anomalies non bloquantes
 */
public record FichierCatalogue(
        FormatImport format,
        List<InventaireDisponible> inventaires,
        InventaireDisponible inventaireRetenu,
        List<LigneCatalogue> lignes,
        List<String> erreurs,
        List<String> avertissements) {}
