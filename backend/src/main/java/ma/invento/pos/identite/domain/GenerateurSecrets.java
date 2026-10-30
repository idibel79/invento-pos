package ma.invento.pos.identite.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Secrets aléatoires : mots de passe temporaires, codes de secours, jetons de rafraîchissement. */
public final class GenerateurSecrets {

    /** Sans caractères ambigus à la lecture (0/O, 1/I/l). */
    private static final String ALPHABET_CODE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final String ALPHABET_MOT_DE_PASSE = ALPHABET_CODE + "abcdefghjkmnpqrstuvwxyz";
    private static final int GROUPES_CODE = 4;
    private static final int TAILLE_GROUPE_CODE = 5;
    private static final int TAILLE_MOT_DE_PASSE = 14;
    private static final int OCTETS_JETON = 32;

    private final SecureRandom random = new SecureRandom();

    /** Mot de passe temporaire lisible, à changer à la première connexion. */
    public String motDePasseTemporaire() {
        return tirer(ALPHABET_MOT_DE_PASSE, TAILLE_MOT_DE_PASSE);
    }

    /** Code de secours au format XXXXX-XXXXX-XXXXX-XXXXX (100 bits d'entropie). */
    public String codeSecours() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < GROUPES_CODE; i++) {
            if (i > 0) code.append('-');
            code.append(tirer(ALPHABET_CODE, TAILLE_GROUPE_CODE));
        }
        return code.toString();
    }

    public String jetonRafraichissement() {
        byte[] octets = new byte[OCTETS_JETON];
        random.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    /** Empreinte d'un jeton à forte entropie : SHA-256 suffit (pas besoin d'un hachage lent). */
    public static String empreinte(String jeton) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(jeton.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private String tirer(String alphabet, int taille) {
        StringBuilder resultat = new StringBuilder(taille);
        for (int i = 0; i < taille; i++) {
            resultat.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return resultat.toString();
    }
}
