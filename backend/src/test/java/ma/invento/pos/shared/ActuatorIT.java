package ma.invento.pos.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ma.invento.pos.IntegrationTest;
import org.junit.jupiter.api.Test;

class ActuatorIT extends IntegrationTest {

    @Test
    void sante_publique_et_identifiant_de_requete_repris() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Request-Id", "caisse-1-abcdef12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().string("X-Request-Id", "caisse-1-abcdef12"));
    }

    @Test
    void autres_endpoints_actuator_fermes() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void contrat_openapi_publie() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/produits']").exists());
    }
}
