package ma.invento.pos.shared.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tâches planifiées, verrouillées par ShedLock dans PostgreSQL (table {@code shedlock}) pour ne
 * tourner que sur une instance. Absentes de la commande {@code --reset-admin} (sans serveur web).
 */
@Configuration
@ConditionalOnWebApplication
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
class PlanificationConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource, @Value("${spring.flyway.default-schema}") String schema) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName(schema + ".shedlock")
                .usingDbTime()
                .build());
    }
}
