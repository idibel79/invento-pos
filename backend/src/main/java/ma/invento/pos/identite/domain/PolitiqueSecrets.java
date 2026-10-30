package ma.invento.pos.identite.domain;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import ma.invento.pos.shared.domain.RegleMetierException;

/** Règles sur les mots de passe, PIN et codes de secours saisis. */
public final class PolitiqueSecrets {

    public static final int MOT_DE_PASSE_MIN = 8;

    /** BCrypt ignore les octets au-delà de 72. */
    public static final int MOT_DE_PASSE_MAX = 72;

    private static final Pattern PIN = Pattern.compile("\\d{4}");

    private PolitiqueSecrets() {}

    public static void verifierMotDePasse(String login, String motDePasse) {
        if (motDePasse == null || motDePasse.length() < MOT_DE_PASSE_MIN) {
            throw new RegleMetierException(
                    "Le mot de passe doit contenir au moins " + MOT_DE_PASSE_MIN + " caractères.");
        }
        if (motDePasse.getBytes(StandardCharsets.UTF_8).length > MOT_DE_PASSE_MAX) {
            throw new RegleMetierException("Le mot de passe est trop long.");
        }
        if (motDePasse.equalsIgnoreCase(login)) {
            throw new RegleMetierException("Le mot de passe doit être différent de l'identifiant.");
        }
    }

    public static void verifierPin(String pin) {
        if (pin == null || !PIN.matcher(pin).matches()) {
            throw new RegleMetierException("Le PIN doit comporter exactement 4 chiffres.");
        }
    }

    /** Le code de secours se saisit sans tenir compte de la casse, des espaces ni des tirets. */
    public static String normaliserCodeSecours(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
