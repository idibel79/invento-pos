package ma.invento.pos.identite.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** JWT signés en HS256 avec une clé partagée par toutes les instances de l'API. */
@Configuration
class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);
    private static final int TAILLE_MIN_CLE = 32;

    @Bean
    SecretKey jwtSecretKey(SecurityProperties properties) {
        String secret = properties.jwtSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("POS_JWT_SECRET absent : clé éphémère générée, les sessions ne survivront pas à un redémarrage.");
            byte[] octets = new byte[TAILLE_MIN_CLE];
            new SecureRandom().nextBytes(octets);
            return new SecretKeySpec(octets, "HmacSHA256");
        }
        byte[] octets = secret.getBytes(StandardCharsets.UTF_8);
        if (octets.length < TAILLE_MIN_CLE) {
            throw new IllegalStateException("POS_JWT_SECRET doit faire au moins " + TAILLE_MIN_CLE + " caractères.");
        }
        return new SecretKeySpec(octets, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
