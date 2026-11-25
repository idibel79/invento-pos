package ma.invento.pos.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ma.invento.pos.IntegrationTest;
import org.junit.jupiter.api.Test;

/** Sans proxy de confiance (réglage par défaut, Raspberry Pi en direct). */
class EnTetesSecuriteIT extends IntegrationTest {

    @Test
    void hsts_envoye_en_https() throws Exception {
        mvc.perform(get("/actuator/health").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    /** L'aperçu local et l'installation en HTTP sur le réseau du site ne doivent pas recevoir HSTS. */
    @Test
    void pas_de_hsts_en_http() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    /** Sans proxy, un X-Forwarded-Proto vient du client : il n'est pas cru. */
    @Test
    void x_forwarded_proto_ignore_sans_proxy_de_confiance() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
