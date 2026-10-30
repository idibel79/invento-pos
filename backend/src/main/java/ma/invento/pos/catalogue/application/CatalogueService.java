package ma.invento.pos.catalogue.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import ma.invento.pos.catalogue.api.CatalogueDtos.ProduitCommande;
import ma.invento.pos.catalogue.api.CatalogueDtos.ProduitDto;
import ma.invento.pos.catalogue.api.CatalogueDtos.ReferenceDto;
import ma.invento.pos.catalogue.api.CatalogueDtos.TvaDto;
import ma.invento.pos.catalogue.domain.Categorie;
import ma.invento.pos.catalogue.domain.FicheProduit;
import ma.invento.pos.catalogue.domain.Produit;
import ma.invento.pos.catalogue.domain.Zone;
import ma.invento.pos.catalogue.infrastructure.CategorieRepository;
import ma.invento.pos.catalogue.infrastructure.ProduitRepository;
import ma.invento.pos.catalogue.infrastructure.ProduitSpecifications;
import ma.invento.pos.catalogue.infrastructure.ProduitSpecifications.Disponibilite;
import ma.invento.pos.catalogue.infrastructure.TvaRepository;
import ma.invento.pos.catalogue.infrastructure.ZoneRepository;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.shared.api.PageResponse;
import ma.invento.pos.shared.domain.ConflitException;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogueService {

    private final ProduitRepository produits;
    private final CategorieRepository categories;
    private final ZoneRepository zones;
    private final TvaRepository tvas;
    private final JournalService journal;
    private final Clock clock;

    public CatalogueService(
            ProduitRepository produits,
            CategorieRepository categories,
            ZoneRepository zones,
            TvaRepository tvas,
            JournalService journal,
            Clock clock) {
        this.produits = produits;
        this.categories = categories;
        this.zones = zones;
        this.tvas = tvas;
        this.journal = journal;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProduitDto> rechercher(
            String recherche,
            Long zoneId,
            Long categorieId,
            Disponibilite disponibilite,
            Boolean actif,
            Pageable pageable,
            boolean voirPrixAchat) {
        var spec = ProduitSpecifications.criteres(recherche, zoneId, categorieId, disponibilite, actif);
        return PageResponse.of(produits.findAll(spec, pageable), versDto(voirPrixAchat));
    }

    @Transactional(readOnly = true)
    public ProduitDto parId(long id, boolean voirPrixAchat) {
        return versDto(voirPrixAchat).apply(charger(id));
    }

    @Transactional(readOnly = true)
    public ProduitDto parCodeBarre(String code, boolean voirPrixAchat) {
        return produits.findByCodeBarre(code.trim())
                .map(versDto(voirPrixAchat))
                .orElseThrow(() -> new RessourceIntrouvableException("Aucun produit pour ce code-barre."));
    }

    @Transactional
    public ProduitDto creer(ProduitCommande commande, long auteurId) {
        if (produits.existsByPid(commande.pid().trim())) {
            throw new ConflitException("Le PID " + commande.pid() + " est déjà utilisé.");
        }
        Instant maintenant = Instant.now(clock);
        Produit produit = new Produit(commande.pid().trim(), maintenant);
        produit.modifierFiche(fiche(commande, null), maintenant);
        produits.save(produit);
        affecterCodesBarres(produit, commande.codesBarres());
        journal.enregistrer(auteurId, ActionJournal.PRODUIT_CREE, "PID " + produit.getPid());
        return versDto(true).apply(produit);
    }

    @Transactional
    public ProduitDto modifier(long id, ProduitCommande commande, long auteurId) {
        Produit produit = charger(id);
        if (commande.version() != null && commande.version() != produit.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Produit.class, id);
        }
        if (!produit.getPid().equals(commande.pid().trim())) {
            throw new ConflitException("Le PID d'un produit ne peut pas être modifié.");
        }
        produit.modifierFiche(fiche(commande, produit.getTauxMarge()), Instant.now(clock));
        affecterCodesBarres(produit, commande.codesBarres());
        journal.enregistrer(auteurId, ActionJournal.PRODUIT_MODIFIE, "PID " + produit.getPid());
        return versDto(true).apply(produit);
    }

    @Transactional(readOnly = true)
    public List<ReferenceDto> categories() {
        return categories.findAllByOrderByNomAsc().stream()
                .map(c -> new ReferenceDto(c.getId(), c.getNom()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReferenceDto> zones() {
        return zones.findAllByOrderByNomAsc().stream()
                .map(z -> new ReferenceDto(z.getId(), z.getNom()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TvaDto> tvas() {
        return tvas.findAllByOrderByTauxAsc().stream()
                .map(t -> new TvaDto(t.getId(), t.getLibelle(), t.getTaux()))
                .toList();
    }

    @Transactional
    public ReferenceDto creerCategorie(String nom) {
        String propre = nom.trim();
        if (categories.findByNomIgnoreCase(propre).isPresent()) {
            throw new ConflitException("La catégorie " + propre + " existe déjà.");
        }
        Categorie categorie = categories.save(new Categorie(propre));
        return new ReferenceDto(categorie.getId(), categorie.getNom());
    }

    @Transactional
    public ReferenceDto creerZone(String nom) {
        String propre = nom.trim();
        if (zones.findByNomIgnoreCase(propre).isPresent()) {
            throw new ConflitException("La zone " + propre + " existe déjà.");
        }
        Zone zone = zones.save(new Zone(propre));
        return new ReferenceDto(zone.getId(), zone.getNom());
    }

    private Produit charger(long id) {
        return produits.findWithReferencesById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit introuvable."));
    }

    /** Le taux de marge vient de l'import Sobrus et n'est pas saisi par l'admin : on le conserve. */
    private FicheProduit fiche(ProduitCommande c, BigDecimal tauxMarge) {
        return new FicheProduit(
                c.designation().trim(),
                categories
                        .findById(c.categorieId())
                        .orElseThrow(() -> new RessourceIntrouvableException("Catégorie introuvable.")),
                tvas.findById(c.tvaId()).orElseThrow(() -> new RessourceIntrouvableException("TVA introuvable.")),
                zones.findById(c.zoneId()).orElseThrow(() -> new RessourceIntrouvableException("Zone introuvable.")),
                c.ppv(),
                c.pph(),
                tauxMarge,
                c.stockMin(),
                c.stockMax(),
                c.datePeremption(),
                c.actif());
    }

    private void affecterCodesBarres(Produit produit, List<String> codes) {
        List<String> propres = codes.stream().map(String::trim).distinct().toList();
        if (!propres.isEmpty()
                && !produits.findAutresProduitsAvecCodes(propres, produit.getId())
                        .isEmpty()) {
            throw new ConflitException("Un des codes-barres est déjà attribué à un autre produit.");
        }
        produit.remplacerCodesBarres(propres);
    }

    private static Function<Produit, ProduitDto> versDto(boolean voirPrixAchat) {
        return p -> new ProduitDto(
                p.getId(),
                p.getPid(),
                p.getDesignation(),
                new ReferenceDto(p.getCategorie().getId(), p.getCategorie().getNom()),
                new ReferenceDto(p.getZone().getId(), p.getZone().getNom()),
                new TvaDto(
                        p.getTva().getId(), p.getTva().getLibelle(), p.getTva().getTaux()),
                p.getPpv(),
                voirPrixAchat ? p.getPph() : null,
                voirPrixAchat ? p.getTauxMarge() : null,
                p.getStock(),
                p.getStockReserve(),
                p.getStockDisponible(),
                p.isEnRupture(),
                p.getStockMin(),
                p.getStockMax(),
                p.getDatePeremption(),
                p.isActif(),
                p.getCodesBarres(),
                p.getVersion());
    }
}
