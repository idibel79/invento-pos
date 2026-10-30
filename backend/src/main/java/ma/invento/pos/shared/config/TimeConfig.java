package ma.invento.pos.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TimeConfig {

    /** Toutes les dates sont stockées en UTC ; le fuseau de la boutique est un paramètre. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
