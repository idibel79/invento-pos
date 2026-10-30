package ma.invento.pos.identite.config;

import ma.invento.pos.identite.domain.GenerateurSecrets;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Beans partagés par l'API et par la commande {@code --reset-admin} (sans serveur web). */
@Configuration
@EnableConfigurationProperties({SecurityProperties.class, AdminProperties.class})
class IdentiteConfig {

    @Bean
    GenerateurSecrets generateurSecrets() {
        return new GenerateurSecrets();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
