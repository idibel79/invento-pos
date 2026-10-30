package ma.invento.pos.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Contrat OpenAPI : le client TypeScript du front est généré à partir de ce document. */
@Configuration
class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    OpenAPI posOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Invento POS API").version("v1"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /**
     * Toutes les propriétés des schémas renvoyés par l'API sont présentes dans le JSON (les
     * valeurs absentes valent {@code null}, signalées par {@code @Schema(nullable = true)}) : on
     * les déclare obligatoires pour que le client TypeScript généré n'en fasse pas des options.
     */
    @Bean
    OpenApiCustomizer proprietesDesReponsesObligatoires() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            if (schemas == null || openApi.getPaths() == null) return;
            Deque<Schema<?>> aVisiter = new ArrayDeque<>();
            openApi.getPaths().values().stream()
                    .flatMap(chemin -> chemin.readOperations().stream())
                    .flatMap(operation -> operation.getResponses().values().stream())
                    .filter(reponse -> reponse.getContent() != null)
                    .flatMap(reponse -> reponse.getContent().values().stream())
                    .forEach(media -> aVisiter.add(media.getSchema()));
            Set<String> reponses = new HashSet<>();
            while (!aVisiter.isEmpty()) {
                Schema<?> schema = aVisiter.pop();
                if (schema == null) continue;
                if (schema.get$ref() != null) {
                    String nom = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
                    if (reponses.add(nom) && schemas.containsKey(nom)) aVisiter.push(schemas.get(nom));
                    continue;
                }
                if (schema.getItems() != null) aVisiter.push(schema.getItems());
                if (schema.getProperties() != null)
                    schema.getProperties().values().forEach(aVisiter::push);
            }
            reponses.stream()
                    .map(schemas::get)
                    .filter(schema -> schema != null && schema.getProperties() != null)
                    .forEach(schema -> {
                        schema.setRequired(
                                new ArrayList<>(schema.getProperties().keySet()));
                        @SuppressWarnings("unchecked")
                        Map<String, Schema> proprietes = schema.getProperties();
                        proprietes.replaceAll((nom, propriete) -> referenceNullable(propriete));
                    });
        };
    }

    /**
     * Une référence nullable sort sous la forme {@code {"type": "null", "$ref": ...}}, que les
     * générateurs ignorent : on l'écrit {@code oneOf: [$ref, null]}.
     */
    private static Schema referenceNullable(Schema propriete) {
        boolean nullable = Boolean.TRUE.equals(propriete.getNullable())
                || "null".equals(propriete.getType())
                || (propriete.getTypes() != null && propriete.getTypes().contains("null"));
        if (propriete.get$ref() == null || !nullable) return propriete;
        return new JsonSchema()
                .oneOf(List.of(new JsonSchema().$ref(propriete.get$ref()), new JsonSchema().types(Set.of("null"))));
    }
}
