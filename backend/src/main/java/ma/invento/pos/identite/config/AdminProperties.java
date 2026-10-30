package ma.invento.pos.identite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param motDePasseInitial mot de passe temporaire de l'admin à l'installation ; s'il est vide,
 *     un mot de passe aléatoire est généré et affiché une fois dans la console
 * @param reset vrai quand l'application est lancée avec {@code --reset-admin}
 */
@ConfigurationProperties("pos.admin")
public record AdminProperties(String motDePasseInitial, boolean reset) {}
