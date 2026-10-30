package ma.invento.pos.identite.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages de sécurité, fournis par variables d'environnement (voir {@code .env.example}).
 *
 * @param jwtSecret clé HMAC des JWT (32 caractères minimum) ; obligatoire en production
 * @param accessTokenTtl durée de vie du jeton d'accès
 * @param refreshTokenTtl durée de vie du jeton de rafraîchissement (une journée de caisse)
 * @param cookieSecure cookie de rafraîchissement limité à HTTPS (désactivé seulement en dev)
 * @param corsOrigins origines autorisées à appeler l'API depuis un autre domaine
 */
@ConfigurationProperties("pos.security")
public record SecurityProperties(
        String jwtSecret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("12h") Duration refreshTokenTtl,
        @DefaultValue("true") boolean cookieSecure,
        @DefaultValue List<String> corsOrigins) {}
