package ma.invento.pos.importation.api;

import java.io.IOException;
import java.io.InputStream;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.importation.application.ImportCatalogueService;
import ma.invento.pos.importation.application.RapportImport;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Import Excel du catalogue (admin). L'écran appelle d'abord {@code /analyse} pour afficher le
 * format, les inventaires proposés et les erreurs, puis lance l'import.
 */
@RestController
@RequestMapping(path = "/api/v1/imports/catalogue", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasRole('ADMIN')")
class ImportController {

    private final ImportCatalogueService importService;

    ImportController(ImportCatalogueService importService) {
        this.importService = importService;
    }

    @PostMapping("/analyse")
    RapportImport analyser(
            @RequestPart("fichier") MultipartFile fichier, @RequestParam(required = false) String inventaire) {
        try (InputStream contenu = ouvrir(fichier)) {
            return importService.analyser(contenu, inventaire);
        } catch (IOException e) {
            throw new RegleMetierException("Fichier illisible.");
        }
    }

    @PostMapping
    RapportImport importer(
            @RequestPart("fichier") MultipartFile fichier,
            @RequestParam(required = false) String inventaire,
            @AuthenticationPrincipal Jwt jwt) {
        try (InputStream contenu = ouvrir(fichier)) {
            return importService.importer(
                    contenu,
                    nomFichier(fichier),
                    inventaire,
                    UtilisateurConnecte.depuis(jwt).id());
        } catch (IOException e) {
            throw new RegleMetierException("Fichier illisible.");
        }
    }

    private static InputStream ouvrir(MultipartFile fichier) throws IOException {
        if (fichier.isEmpty()) throw new RegleMetierException("Le fichier est vide.");
        return fichier.getInputStream();
    }

    private static String nomFichier(MultipartFile fichier) {
        String nom = fichier.getOriginalFilename();
        return nom == null || nom.isBlank() ? "fichier.xlsx" : nom.replaceAll("[\\r\\n]", "");
    }
}
