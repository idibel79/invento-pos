package ma.invento.pos.identite.api;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Adresse IP du client pour limiter les essais ratés. Derrière Cloud Run ou un proxy inverse, le
 * proxy ajoute l'adresse réelle en dernier dans {@code X-Forwarded-For} ; les valeurs précédentes
 * viennent du client et peuvent être inventées. On lit donc l'en-tête brut (avant le filtre
 * {@code ForwardedHeaderFilter}, qui retient la première valeur) et on garde la dernière.
 */
final class AdresseClient {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private AdresseClient() {}

    static String de(HttpServletRequest requete) {
        ServletRequest brute = requete;
        while (brute instanceof ServletRequestWrapper enveloppe) {
            brute = enveloppe.getRequest();
        }
        String transmis = brute instanceof HttpServletRequest http ? http.getHeader(X_FORWARDED_FOR) : null;
        if (transmis != null && !transmis.isBlank()) {
            String[] adresses = transmis.split(",");
            String derniere = adresses[adresses.length - 1].trim();
            if (!derniere.isEmpty()) return derniere;
        }
        return brute.getRemoteAddr();
    }
}
