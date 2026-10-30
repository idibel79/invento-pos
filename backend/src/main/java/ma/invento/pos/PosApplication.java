package ma.invento.pos;

import java.util.Arrays;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class PosApplication {

    /** Argument de la commande de secours lancée sur le serveur (cadrage : récupération admin). */
    static final String RESET_ADMIN_ARG = "--reset-admin";

    public static void main(String[] args) {
        if (Arrays.asList(args).contains(RESET_ADMIN_ARG)) {
            runResetAdmin(args);
            return;
        }
        SpringApplication.run(PosApplication.class, args);
    }

    /**
     * Démarre l'application sans serveur web, le temps de réinitialiser le mot de passe admin
     * ({@code ResetAdminRunner}), puis s'arrête.
     */
    private static void runResetAdmin(String[] args) {
        String[] springArgs =
                Arrays.stream(args).filter(arg -> !RESET_ADMIN_ARG.equals(arg)).toArray(String[]::new);
        ConfigurableApplicationContext context = new SpringApplicationBuilder(PosApplication.class)
                .web(WebApplicationType.NONE)
                .properties(Map.of("pos.admin.reset", "true"))
                .run(springArgs);
        System.exit(SpringApplication.exit(context));
    }
}
