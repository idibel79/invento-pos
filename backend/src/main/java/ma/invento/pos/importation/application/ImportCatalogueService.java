package ma.invento.pos.importation.application;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.invento.pos.catalogue.domain.Categorie;
import ma.invento.pos.catalogue.domain.FicheProduit;
import ma.invento.pos.catalogue.domain.Produit;
import ma.invento.pos.catalogue.domain.Tva;
import ma.invento.pos.catalogue.domain.Zone;
import ma.invento.pos.catalogue.infrastructure.CategorieRepository;
import ma.invento.pos.catalogue.infrastructure.ProduitRepository;
import ma.invento.pos.catalogue.infrastructure.TvaRepository;
import ma.invento.pos.catalogue.infrastructure.ZoneRepository;
import ma.invento.pos.importation.domain.AnalyseurFichier;
import ma.invento.pos.importation.domain.FichierCatalogue;
import ma.invento.pos.importation.domain.LigneCatalogue;
import ma.invento.pos.importation.infrastructure.LecteurExcel;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.stock.domain.MouvementStock;
import ma.invento.pos.stock.domain.TypeMouvement;
import ma.invento.pos.stock.infrastructure.MouvementStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Import du catalogue et du stock depuis un export Sobrus ou Invento. Les produits sont repérés
 * par leur PID : créés s'ils sont nouveaux, mis à jour sinon. Toute variation de stock laisse un
 * mouvement {@code IMPORT}. L'import est tout ou rien : les lignes invalides sont écartées avant.
 */
@Service
public class ImportCatalogueService {

    private static final int REFERENCE_MAX = 120;

    private final LecteurExcel lecteur;
    private final ProduitRepository produits;
    private final CategorieRepository categories;
    private final ZoneRepository zones;
    private final TvaRepository tvas;
    private final MouvementStockRepository mouvements;
    private final JournalService journal;
    private final Clock clock;

    public ImportCatalogueService(
            LecteurExcel lecteur,
            ProduitRepository produits,
            CategorieRepository categories,
            ZoneRepository zones,
            TvaRepository tvas,
            MouvementStockRepository mouvements,
            JournalService journal,
            Clock clock) {
        this.lecteur = lecteur;
        this.produits = produits;
        this.categories = categories;
        this.zones = zones;
        this.tvas = tvas;
        this.mouvements = mouvements;
        this.journal = journal;
        this.clock = clock;
    }

    /** Lecture seule : format détecté, inventaires proposés, erreurs, sans rien écrire. */
    public RapportImport analyser(InputStream contenu, String inventaireChoisi) {
        FichierCatalogue fichier = AnalyseurFichier.analyser(lecteur.lirePremiereFeuille(contenu), inventaireChoisi);
        return rapport(fichier, 0, 0, 0, fichier.avertissements());
    }

    @Transactional
    public RapportImport importer(InputStream contenu, String nomFichier, String inventaireChoisi, long auteurId) {
        FichierCatalogue fichier = AnalyseurFichier.analyser(lecteur.lirePremiereFeuille(contenu), inventaireChoisi);
        Instant maintenant = Instant.now(clock);
        Referentiels referentiels = new Referentiels();
        Map<String, Produit> existants =
                produits.findAllBy().stream().collect(Collectors.toMap(Produit::getPid, Function.identity()));
        Map<String, String> pidParCode = new HashMap<>();
        existants.values().forEach(p -> p.getCodesBarres().forEach(code -> pidParCode.put(code, p.getPid())));
        List<String> avertissements = new ArrayList<>(fichier.avertissements());
        String reference = reference(fichier, nomFichier);

        int crees = 0;
        int misAJour = 0;
        List<MouvementStock> nouveauxMouvements = new ArrayList<>();
        Map<Produit, List<String>> codesVoulus = new HashMap<>();
        for (LigneCatalogue ligne : fichier.lignes()) {
            Produit produit = existants.get(ligne.pid());
            boolean nouveau = produit == null;
            if (nouveau) {
                produit = new Produit(ligne.pid(), maintenant);
                crees++;
            } else {
                misAJour++;
            }
            // Un produit désactivé par l'admin le reste après un réimport.
            produit.modifierFiche(fiche(ligne, referentiels, produit.isActif()), maintenant);
            if (nouveau) produits.save(produit);
            int variation = produit.fixerStockImporte(ligne.stock(), maintenant);
            if (variation != 0) {
                nouveauxMouvements.add(new MouvementStock(
                        produit.getId(), TypeMouvement.IMPORT, variation, reference, maintenant, auteurId));
            }
            codesVoulus.put(produit, codesLibres(ligne, pidParCode, avertissements));
        }
        affecterCodesBarres(codesVoulus);
        mouvements.saveAll(nouveauxMouvements);

        RapportImport rapport = rapport(fichier, crees, misAJour, nouveauxMouvements.size(), avertissements);
        journal.enregistrer(
                auteurId,
                ActionJournal.IMPORT_CATALOGUE,
                "%s %s : %d créés, %d mis à jour, %d lignes ignorées"
                        .formatted(
                                fichier.format(),
                                nomFichier,
                                crees,
                                misAJour,
                                fichier.erreurs().size()));
        return rapport;
    }

    /**
     * Un code déjà attribué à un produit absent du fichier reste à ce produit. Les codes qui
     * passent d'un produit à un autre du fichier sont gérés par {@link #affecterCodesBarres}.
     */
    private static List<String> codesLibres(
            LigneCatalogue ligne, Map<String, String> pidParCode, List<String> avertissements) {
        List<String> libres = new ArrayList<>();
        for (String code : ligne.codesBarres()) {
            String proprietaire = pidParCode.get(code);
            if (proprietaire == null || proprietaire.equals(ligne.pid())) {
                libres.add(code);
            } else {
                avertissements.add("Ligne " + ligne.numeroLigne() + " : code-barre " + code
                        + " déjà attribué au produit " + proprietaire + ", ignoré.");
            }
        }
        return libres;
    }

    /**
     * En deux temps pour respecter l'unicité des codes : on retire d'abord les codes qui
     * disparaissent (flush des suppressions), puis on ajoute les nouveaux.
     */
    private void affecterCodesBarres(Map<Produit, List<String>> codesVoulus) {
        codesVoulus.forEach((produit, codes) -> produit.remplacerCodesBarres(
                produit.getCodesBarres().stream().filter(codes::contains).toList()));
        produits.flush();
        codesVoulus.forEach(Produit::remplacerCodesBarres);
    }

    private static FicheProduit fiche(LigneCatalogue ligne, Referentiels referentiels, boolean actif) {
        return new FicheProduit(
                ligne.designation(),
                referentiels.categorie(ligne.categorie()),
                referentiels.tva(ligne.tvaLibelle(), ligne.tvaTaux()),
                referentiels.zone(ligne.zone()),
                ligne.ppv(),
                ligne.pph(),
                ligne.tauxMarge(),
                ligne.stockMin(),
                ligne.stockMax(),
                ligne.datePeremption(),
                actif);
    }

    private static String reference(FichierCatalogue fichier, String nomFichier) {
        String inventaire = fichier.inventaireRetenu() == null
                ? ""
                : " inventaire " + fichier.inventaireRetenu().numero();
        String reference = "Import " + fichier.format() + inventaire + " (" + nomFichier + ")";
        return reference.length() <= REFERENCE_MAX ? reference : reference.substring(0, REFERENCE_MAX);
    }

    private static RapportImport rapport(
            FichierCatalogue fichier, int crees, int misAJour, int nombreMouvements, List<String> avertissements) {
        List<LigneCatalogue> lignes = fichier.lignes();
        return new RapportImport(
                fichier.format(),
                fichier.inventaires(),
                fichier.inventaireRetenu(),
                lignes.size(),
                crees,
                misAJour,
                nombreMouvements,
                (int) lignes.stream().filter(l -> l.stock() < 0).count(),
                (int) lignes.stream().filter(l -> l.codesBarres().isEmpty()).count(),
                fichier.erreurs().size(),
                RapportImport.borner(fichier.erreurs()),
                avertissements.size(),
                RapportImport.borner(avertissements));
    }

    /** Catégories, zones et TVA chargées une fois, créées à la volée (sans requête par ligne). */
    private final class Referentiels {

        private final Map<String, Categorie> categoriesParNom = categories.findAll().stream()
                .collect(Collectors.toMap(c -> cle(c.getNom()), Function.identity(), (a, b) -> a));
        private final Map<String, Zone> zonesParNom = zones.findAll().stream()
                .collect(Collectors.toMap(z -> cle(z.getNom()), Function.identity(), (a, b) -> a));
        private final Map<BigDecimal, Tva> tvasParTaux = tvas.findAll().stream()
                .collect(Collectors.toMap(t -> t.getTaux().stripTrailingZeros(), Function.identity(), (a, b) -> a));

        Categorie categorie(String nom) {
            return categoriesParNom.computeIfAbsent(cle(nom), k -> categories.save(new Categorie(nom)));
        }

        Zone zone(String nom) {
            return zonesParNom.computeIfAbsent(cle(nom), k -> zones.save(new Zone(nom)));
        }

        Tva tva(String libelle, BigDecimal taux) {
            return tvasParTaux.computeIfAbsent(taux.stripTrailingZeros(), k -> tvas.save(new Tva(libelle, taux)));
        }

        private static String cle(String nom) {
            return nom.toLowerCase(Locale.ROOT);
        }
    }
}
