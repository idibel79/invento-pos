package ma.invento.pos.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ma.invento.pos.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Derrière un proxy de confiance (Cloud Run) : le HTTPS du proxy est reconnu. */
@TestPropertySource(properties = "pos.security.proxy-confiance=true")
class ProxyConfianceIT extends IntegrationTest {

    @Test
    void hsts_envoye_derriere_le_proxy_https() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    void pas_de_hsts_quand_le_proxy_recoit_du_http() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Forwarded-Proto", "http"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
