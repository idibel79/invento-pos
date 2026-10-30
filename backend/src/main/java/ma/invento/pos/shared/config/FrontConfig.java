package ma.invento.pos.shared.config;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Le build React est servi par l'application ({@code classpath:/static}). Les fichiers de
 * {@code /assets} portent une empreinte dans leur nom : mis en cache un an. {@code index.html}
 * est revalidé à chaque chargement ({@code spring.web.resources.cache}) pour suivre les mises à jour.
 */
@Configuration
class FrontConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
