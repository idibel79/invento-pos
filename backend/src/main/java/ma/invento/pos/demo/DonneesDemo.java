package ma.invento.pos.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import ma.invento.pos.caisse.application.CaisseService;
import ma.invento.pos.caisse.infrastructure.CaisseRepository;
import ma.invento.pos.catalogue.api.CatalogueDtos.ProduitCommande;
import ma.invento.pos.catalogue.application.CatalogueService;
import ma.invento.pos.catalogue.domain.Produit;
import ma.invento.pos.catalogue.domain.Tva;
import ma.invento.pos.catalogue.infrastructure.ProduitRepository;
import ma.invento.pos.catalogue.infrastructure.TvaRepository;
import ma.invento.pos.client.api.ClientDtos.ClientCommande;
import ma.invento.pos.client.application.ClientService;
import ma.invento.pos.client.domain.TypeClient;
import ma.invento.pos.identite.api.UtilisateurDtos.CreationUtilisateur;
import ma.invento.pos.identite.api.UtilisateurDtos.SeuilsRemiseDto;
import ma.invento.pos.identite.application.UtilisateurService;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.SeuilsRemise;
import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
import ma.invento.pos.parametre.application.ParametreService;
import ma.invento.pos.parametre.application.ParametreService.Parametres;
import ma.invento.pos.stock.domain.MouvementStock;
import ma.invento.pos.stock.domain.TypeMouvement;
import ma.invento.pos.stock.infrastructure.MouvementStockRepository;
import ma.invento.pos.tresorerie.application.TresorerieService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Données de démonstration pour prévisualiser une PR en local ({@code scripts/preview}) : comptes
 * aux mots de passe connus et déjà choisis, deux caisses et quelques produits en stock. Profil
 * {@code demo} uniquement, jamais avec {@code prod}. Rien n'est créé si une caisse existe déjà.
 */
@Component
@Profile("demo & !prod")
public class DonneesDemo {

    public static final String MOT_DE_PASSE = "demo1234";
    public static final String PIN_ADMIN = "0000";
    public static final String PIN_RESPONSABLE = "1234";

    private static final Logger log = LoggerFactory.getLogger(DonneesDemo.class);
    private static final String REFERENCE_STOCK = "Données de démo";

    private final UtilisateurRepository utilisateurs;
    private final UtilisateurService utilisateurService;
    private final CaisseRepository caisses;
    private final CaisseService caisseService;
    private final CatalogueService catalogue;
    private final TvaRepository tvas;
    private final ProduitRepository produits;
    private final MouvementStockRepository mouvements;
    private final TresorerieService tresorerie;
    private final ClientService clients;
    private final ParametreService parametres;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public DonneesDemo(
            UtilisateurRepository utilisateurs,
            UtilisateurService utilisateurService,
            CaisseRepository caisses,
            CaisseService caisseService,
            CatalogueService catalogue,
            TvaRepository tvas,
            ProduitRepository produits,
            MouvementStockRepository mouvements,
            TresorerieService tresorerie,
            ClientService clients,
            ParametreService parametres,
            PasswordEncoder encoder,
            Clock clock) {
        this.utilisateurs = utilisateurs;
        this.utilisateurService = utilisateurService;
        this.caisses = caisses;
        this.caisseService = caisseService;
        this.catalogue = catalogue;
        this.tvas = tvas;
        this.produits = produits;
        this.mouvements = mouvements;
        this.tresorerie = tresorerie;
        this.clients = clients;
        this.parametres = parametres;
        this.encoder = encoder;
        this.clock = clock;
    }

    /** Après les {@code ApplicationRunner}, donc après la création du compte admin. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void creerSiBaseVide() {
        if (caisses.count() == 0) creerBase();
        creerTresorerieSiVide();
        creerClientsSiVide();
    }

    private void creerBase() {
        Utilisateur admin = utilisateurs.findByRole(Role.ADMIN).orElseThrow();
        long adminId = admin.getId();
        admin.changerMotDePasse(encoder.encode(MOT_DE_PASSE));
        utilisateurService.definirPin(adminId, PIN_ADMIN, adminId);

        long caisse1 = caisseService.creer("Caisse 1", adminId).id();
        long caisse2 = caisseService.creer("Caisse 2", adminId).id();
        creerCompte(
                "caissier",
                "Caissier",
                Role.CAISSIER,
                Set.of(caisse1),
                null,
                SeuilsRemiseDto.depuis(SeuilsRemise.AUCUN),
                adminId);
        creerCompte(
                "responsable",
                "Responsable",
                Role.RESPONSABLE,
                Set.of(caisse2),
                PIN_RESPONSABLE,
                new SeuilsRemiseDto(BigDecimal.TEN, BigDecimal.valueOf(5)),
                adminId);

        creerCatalogue(adminId);
        parametres.modifier(
                new Parametres(
                        "Boutique de démo",
                        "12, avenue Mohammed V, Rabat",
                        "05 37 00 00 00",
                        "contact@boutique-demo.ma",
                        "002345678000012",
                        "40123456",
                        "RC 98765",
                        "25412345",
                        "Merci de votre visite, à bientôt !",
                        parametres.lire().fuseauHoraire()),
                adminId);
        log.info("Données de démo créées : comptes admin, responsable, caissier (mot de passe {}).", MOT_DE_PASSE);
    }

    /**
     * Caisse principale alimentée de 20 000 DH ; les comptes des caissiers partent de 0 DH (l'admin
     * leur verse leur fond). Aussi sur une base démo conservée, tant que le journal de
     * la caisse principale est vide.
     */
    private void creerTresorerieSiVide() {
        if (tresorerie.journal(null, null, null, null, false, 0, 1).totalElements() > 0) return;
        long adminId = utilisateurs.findByRole(Role.ADMIN).orElseThrow().getId();
        tresorerie.alimenter(new BigDecimal("20000.00"), "Fonds de démo", null, adminId);
        log.info("Caisse principale de démo alimentée ; les comptes des caissiers partent de 0 DH.");
    }

    /**
     * Annuaire de démo (sans dette : elle vient des ventes à crédit), aussi sur une base conservée sans
     * client. Une entreprise de démo est ajoutée à un annuaire qui n'en a pas encore.
     */
    private void creerClientsSiVide() {
        long adminId = utilisateurs.findByRole(Role.ADMIN).orElseThrow().getId();
        if (clients.lister(null, true).isEmpty()) {
            clients.creer(particulier("Hassan Alami", "06 61 23 45 78", "Voisin, paie en fin de mois"), adminId);
            clients.creer(particulier("Fatima Zahra Bennani", "06 70 11 02 33", null), adminId);
            clients.creer(particulier("Khadija Ouazzani", "07 01 55 66 77", null), adminId);
            clients.creer(particulier("Rachid El Fassi", "06 44 32 10 09", "Chauffeur"), adminId);
            log.info("Annuaire de démo créé.");
        }
        if (clients.lister(null, true).stream().noneMatch(c -> c.type() == TypeClient.ENTREPRISE)) {
            clients.creer(
                    new ClientCommande(
                            TypeClient.ENTREPRISE,
                            "Épicerie Atlas SARL",
                            "05 22 40 18 90",
                            "Revendeur, facture mensuelle",
                            null,
                            null,
                            "001234567000089",
                            "1234567",
                            "RC 45678",
                            "Rachid Amrani",
                            "12, rue Ibn Sina, Casablanca",
                            "contact@epicerie-atlas.ma"),
                    adminId);
            log.info("Entreprise de démo ajoutée à l'annuaire.");
        }
    }

    private static ClientCommande particulier(String nom, String telephone, String note) {
        return new ClientCommande(
                TypeClient.PARTICULIER, nom, telephone, note, null, null, null, null, null, null, null, null);
    }

    private void creerCompte(
            String login,
            String prenom,
            Role role,
            Set<Long> caisseIds,
            String pin,
            SeuilsRemiseDto seuils,
            long adminId) {
        long id = utilisateurService
                .creer(
                        new CreationUtilisateur("Démo", prenom, login, role, MOT_DE_PASSE, caisseIds, pin, seuils),
                        adminId)
                .id();
        utilisateurs.findById(id).orElseThrow().changerMotDePasse(encoder.encode(MOT_DE_PASSE));
    }

    private void creerCatalogue(long adminId) {
        long tva7 = tvas.save(new Tva("TVA 7 %", new BigDecimal("7.00"))).getId();
        long tva20 = tvas.save(new Tva("TVA 20 %", new BigDecimal("20.00"))).getId();
        long medicaments = catalogue.creerCategorie("Médicaments").id();
        long parapharmacie = catalogue.creerCategorie("Parapharmacie").id();
        long comptoir = catalogue.creerZone("Comptoir").id();
        long rayon = catalogue.creerZone("Rayon").id();

        List<ProduitDemo> liste = List.of(
                new ProduitDemo(
                        "DEMO-001",
                        "Doliprane 1000 mg, 8 comprimés",
                        medicaments,
                        tva7,
                        comptoir,
                        "15.80",
                        120,
                        "6118000010019"),
                new ProduitDemo(
                        "DEMO-002",
                        "Efferalgan 500 mg, 16 comprimés",
                        medicaments,
                        tva7,
                        comptoir,
                        "19.90",
                        60,
                        "6118000010026"),
                new ProduitDemo(
                        "DEMO-003", "Smecta, 12 sachets", medicaments, tva7, comptoir, "34.50", 25, "6118000010033"),
                new ProduitDemo(
                        "DEMO-004",
                        "Vitamine C 1000 mg, 20 comprimés",
                        parapharmacie,
                        tva20,
                        rayon,
                        "45.00",
                        40,
                        "6118000010040"),
                new ProduitDemo(
                        "DEMO-005",
                        "Crème hydratante 50 ml",
                        parapharmacie,
                        tva20,
                        rayon,
                        "89.00",
                        12,
                        "6118000010057"),
                new ProduitDemo(
                        "DEMO-006",
                        "Gel hydroalcoolique 100 ml",
                        parapharmacie,
                        tva20,
                        rayon,
                        "12.00",
                        200,
                        "6118000010064"),
                new ProduitDemo(
                        "DEMO-007",
                        "Sirop contre la toux 125 ml",
                        medicaments,
                        tva7,
                        comptoir,
                        "42.30",
                        2,
                        "6118000010071"),
                new ProduitDemo(
                        "DEMO-008",
                        "Pansements assortis, boîte de 30",
                        parapharmacie,
                        tva20,
                        rayon,
                        "27.50",
                        0,
                        "6118000010088"));
        Instant maintenant = Instant.now(clock);
        for (ProduitDemo p : liste) {
            long id = catalogue
                    .creer(
                            new ProduitCommande(
                                    p.pid(),
                                    p.designation(),
                                    p.categorieId(),
                                    p.tvaId(),
                                    p.zoneId(),
                                    new BigDecimal(p.ppv()),
                                    null,
                                    null,
                                    null,
                                    null,
                                    true,
                                    List.of(p.codeBarre()),
                                    null),
                            adminId)
                    .id();
            Produit produit = produits.findById(id).orElseThrow();
            int variation = produit.fixerStockImporte(p.stock(), maintenant);
            if (variation != 0) {
                mouvements.save(
                        new MouvementStock(id, TypeMouvement.IMPORT, variation, REFERENCE_STOCK, maintenant, adminId));
            }
        }
    }

    private record ProduitDemo(
            String pid,
            String designation,
            long categorieId,
            long tvaId,
            long zoneId,
            String ppv,
            int stock,
            String codeBarre) {}
}
