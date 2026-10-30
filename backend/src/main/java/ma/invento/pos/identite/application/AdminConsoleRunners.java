package ma.invento.pos.identite.application;

import java.io.PrintStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Affiche dans la console, une seule fois, les secrets de l'installation ou de la commande
 * {@code --reset-admin}. Ils passent par la sortie standard et jamais par les logs applicatifs.
 */
@Configuration
class AdminConsoleRunners {

    private static final Logger log = LoggerFactory.getLogger(AdminConsoleRunners.class);
    private static final String CADRE = "=".repeat(64);

    @Bean
    @ConditionalOnProperty(name = "pos.admin.reset", havingValue = "false", matchIfMissing = true)
    ApplicationRunner installationAdmin(AdministrationAdminService service) {
        return args -> {
            try {
                service.installerSiNecessaire()
                        .ifPresent(secrets -> afficher(
                                "INSTALLATION : compte administrateur créé",
                                "Identifiant          : " + secrets.login(),
                                "Mot de passe initial : " + secrets.motDePasseTemporaire()
                                        + "  (à changer à la 1re connexion)",
                                "Code de secours      : " + secrets.codeSecours(),
                                "Notez ce code hors de l'ordinateur : il ne sera plus jamais affiché.",
                                "Il permet de redéfinir le mot de passe admin en cas d'oubli."));
            } catch (DataIntegrityViolationException e) {
                // Une autre instance a créé l'admin au même moment.
                log.info("Compte admin déjà créé par une autre instance.");
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "pos.admin.reset", havingValue = "true")
    ApplicationRunner reinitialisationAdmin(AdministrationAdminService service) {
        return args -> {
            var secrets = service.reinitialiserAdmin();
            afficher(
                    "RÉINITIALISATION du mot de passe administrateur",
                    "Identifiant           : " + secrets.login(),
                    "Mot de passe temporaire : " + secrets.motDePasseTemporaire() + "  (à changer à la connexion)",
                    "Toutes les sessions de l'admin ont été fermées. Action inscrite au journal.");
        };
    }

    @SuppressWarnings("java:S106") // affichage console volontaire, hors logs
    private static void afficher(String... lignes) {
        PrintStream out = System.out;
        out.println();
        out.println(CADRE);
        for (String ligne : lignes) out.println("  " + ligne);
        out.println(CADRE);
        out.println();
    }
}
