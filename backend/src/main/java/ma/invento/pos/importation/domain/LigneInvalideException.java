package ma.invento.pos.importation.domain;

/** Ligne du fichier impossible à importer ; elle est ignorée et signalée dans le rapport. */
public class LigneInvalideException extends RuntimeException {

    public LigneInvalideException(String message) {
        super(message);
    }
}
