package ma.invento.pos.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import ma.invento.pos.IntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Exporte le contrat OpenAPI dans {@code frontend/openapi.json}, d'où le client TypeScript est
 * généré. La CI échoue si le fichier versionné diffère de celui produit par le code.
 */
class OpenApiContratIT extends IntegrationTest {

    private static final Path CONTRAT = Path.of("..", "frontend", "openapi.json");

    @Test
    void exporte_le_contrat_openapi() throws Exception {
        String brut = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String lisible = json.writerWithDefaultPrettyPrinter().writeValueAsString(json.readTree(brut)) + "\n";
        if (Files.isDirectory(CONTRAT.getParent())) {
            Files.writeString(CONTRAT, lisible, StandardCharsets.UTF_8);
        }
    }
}
