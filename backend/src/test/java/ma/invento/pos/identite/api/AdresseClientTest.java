package ma.invento.pos.identite.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AdresseClientTest {

    private static MockHttpServletRequest requete(String xForwardedFor) {
        MockHttpServletRequest requete = new MockHttpServletRequest();
        requete.setRemoteAddr("192.168.1.20");
        if (xForwardedFor != null) requete.addHeader("X-Forwarded-For", xForwardedFor);
        return requete;
    }

    @Test
    void sans_proxy_de_confiance_l_en_tete_invente_est_ignore() {
        assertThat(AdresseClient.de(requete("1.2.3.4"), false)).isEqualTo("192.168.1.20");
    }

    @Test
    void derriere_le_proxy_la_derniere_adresse_ajoutee_par_le_proxy_compte() {
        assertThat(AdresseClient.de(requete("1.2.3.4, 41.250.10.7"), true)).isEqualTo("41.250.10.7");
    }

    @Test
    void derriere_le_proxy_sans_en_tete_l_adresse_de_connexion_compte() {
        assertThat(AdresseClient.de(requete(null), true)).isEqualTo("192.168.1.20");
    }

    @Test
    void la_requete_enveloppee_par_un_filtre_est_lue_brute() {
        var enveloppee = new HttpServletRequestWrapper(requete("41.250.10.7")) {
            @Override
            public String getRemoteAddr() {
                return "8.8.8.8";
            }
        };
        assertThat(AdresseClient.de(enveloppee, false)).isEqualTo("192.168.1.20");
    }
}
