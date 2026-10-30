package ma.invento.pos.importation.domain;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Répare le texte encodé deux fois en UTF-8 des exports Sobrus ({@code "CatÃ©gorie"} au lieu de
 * {@code "Catégorie"}), comme {@code XlsxReader.repairMojibake} dans Invento.
 *
 * <p>Les octets UTF-8 d'origine ont été lus une première fois en Latin-1 puis ré-encodés en
 * UTF-8. On inverse l'opération : les caractères sont repris comme octets Latin-1 puis décodés en
 * UTF-8. Si le résultat n'est pas un UTF-8 valide, le texte était correct et il est gardé tel quel.
 */
public final class ReparationEncodage {

    private static final int DERNIER_CARACTERE_LATIN1 = 0xFF;

    private ReparationEncodage() {}

    public static String reparer(String texte) {
        if (texte == null || texte.isEmpty() || texte.chars().anyMatch(c -> c > DERNIER_CARACTERE_LATIN1)) {
            return texte;
        }
        byte[] octets = texte.getBytes(StandardCharsets.ISO_8859_1);
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(octets))
                    .toString();
        } catch (CharacterCodingException e) {
            return texte;
        }
    }
}
