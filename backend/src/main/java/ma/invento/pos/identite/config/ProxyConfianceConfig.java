package ma.invento.pos.identite.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Derrière un proxy de confiance seulement ({@code POS_PROXY_CONFIANCE=true}, Cloud Run) : les en-têtes
 * {@code X-Forwarded-*} du proxy donnent le schéma (https, donc HSTS) et l'hôte d'origine. Sans proxy
 * (Raspberry Pi en direct), ils sont ignorés.
 */
@Configuration
@ConditionalOnWebApplication
@ConditionalOnProperty(name = "pos.security.proxy-confiance", havingValue = "true")
class ProxyConfianceConfig {

    @Bean
    FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> inscription =
                new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        // Avant la sécurité, comme le filtre que Spring Boot enregistre lui-même.
        inscription.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return inscription;
    }
}
