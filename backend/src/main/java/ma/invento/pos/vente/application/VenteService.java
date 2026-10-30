package ma.invento.pos.vente.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.invento.pos.caisse.application.SessionCaisseCloturee;
import ma.invento.pos.caisse.application.SessionCaisseService;
import ma.invento.pos.caisse.application.SessionCaisseService.SessionOuverte;
import ma.invento.pos.catalogue.domain.Produit;
import ma.invento.pos.catalogue.infrastructure.ProduitRepository;
import ma.invento.pos.identite.application.AutorisationService;
import ma.invento.pos.identite.application.AutorisationService.Autorisation;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.parametre.application.ParametreService;
import ma.invento.pos.parametre.application.ParametreService.Parametres;
import ma.invento.pos.parametre.domain.PlafondsRemise;
import ma.invento.pos.shared.domain.ConflitException;
import ma.invento.pos.shared.domain.OperationInterditeException;
import ma.invento.pos.shared.domain.RegleMetierException;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import ma.invento.pos.stock.application.StockService;
import ma.invento.pos.stock.application.StockService.Quantite;
import ma.invento.pos.vente.api.VenteDtos.LigneCommande;
import ma.invento.pos.vente.api.VenteDtos.LigneDto;
import ma.invento.pos.vente.api.VenteDtos.MiseEnAttenteCommande;
import ma.invento.pos.vente.api.VenteDtos.PaiementDto;
import ma.invento.pos.vente.api.VenteDtos.PanierCommande;
import ma.invento.pos.vente.api.VenteDtos.PanierDto;
import ma.invento.pos.vente.api.VenteDtos.RemiseDto;
import ma.invento.pos.vente.api.VenteDtos.TotauxDto;
import ma.invento.pos.vente.api.VenteDtos.ValidationCommande;
import ma.invento.pos.vente.api.VenteDtos.VenteDto;
import ma.invento.pos.vente.api.VenteDtos.VentilationTvaDto;
import ma.invento.pos.vente.domain.CalculateurVente;
import ma.invento.pos.vente.domain.LignePanier;
import ma.invento.pos.vente.domain.LigneVente;
import ma.invento.pos.vente.domain.NiveauAutorisation;
import ma.invento.pos.vente.domain.PanierCalcule;
import ma.invento.pos.vente.domain.PanierCalcule.LigneCalculee;
import ma.invento.pos.vente.domain.Remise;
import ma.invento.pos.vente.domain.StatutVente;
import ma.invento.pos.vente.domain.Vente;
import ma.invento.pos.vente.infrastructure.NumeroteurVente;
import ma.invento.pos.vente.infrastructure.VenteRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vente au comptoir : aperçu du panier, validation en espèces, mise en attente avec stock réservé,
 * reprise sur la même caisse, abandon et échéance. Chaque opération qui touche au stock tient
 * dans une seule transaction (vente, lignes, paiement, stock et mouvements).
 */
@Service
public class VenteService {

    /** Durée maximale de réservation du stock d'une vente en attente. */
    public static final Duration DUREE_ATTENTE = Duration.ofMinutes(5);

    private final VenteRepository ventes;
    private final ProduitRepository produits;
    private final UtilisateurRepository utilisateurs;
    private final SessionCaisseService sessions;
    private final StockService stock;
    private final ParametreService parametres;
    private final AutorisationService autorisations;
    private final NumeroteurVente numeroteur;
    private final JournalService journal;
    private final Clock clock;

    public VenteService(
            VenteRepository ventes,
            ProduitRepository produits,
            UtilisateurRepository utilisateurs,
            SessionCaisseService sessions,
            StockService stock,
            ParametreService parametres,
            AutorisationService autorisations,
            NumeroteurVente numeroteur,
            JournalService journal,
            Clock clock) {
        this.ventes = ventes;
        this.produits = produits;
        this.utilisateurs = utilisateurs;
        this.sessions = sessions;
        this.stock = stock;
        this.parametres = parametres;
        this.autorisations = autorisations;
        this.numeroteur = numeroteur;
        this.journal = journal;
        this.clock = clock;
    }

    /** Aperçu du panier : rien n'est enregistré ni réservé. */
    @Transactional(readOnly = true)
    public PanierDto calculer(PanierCommande commande, UtilisateurConnecte vendeur) {
        Vente reprise = commande.venteEnAttenteId() == null
                ? null
                : ventes.findById(commande.venteEnAttenteId())
                        .filter(v -> v.estEnAttente() && v.getCaissierId() == vendeur.id())
                        .orElseThrow(() -> new RessourceIntrouvableException("Vente en attente introuvable."));
        Panier panier = preparer(commande, reprise);
        NiveauAutorisation niveau = niveauRequis(panier.calcul());
        boolean acquise;
        try {
            valideePar(niveau, commande, vendeur, reprise);
            acquise = true;
        } catch (RegleMetierException | OperationInterditeException e) {
            acquise = false;
        }
        Map<Long, Integer> reservees = reprise == null ? Map.of() : quantitesParProduit(reprise);
        List<LigneDto> lignes = panier.calcul().lignes().stream()
                .map(l -> {
                    Produit p = panier.produits().get(l.ligne().produitId());
                    int disponible = p.getStockDisponible() + reservees.getOrDefault(p.getId(), 0);
                    return ligneDto(
                            l, p.getDesignation(), disponible >= l.ligne().quantite());
                })
                .toList();
        return new PanierDto(
                lignes,
                totaux(panier.calcul()),
                panier.calcul().tauxRemiseMax(),
                niveau,
                acquise,
                lignes.stream().allMatch(LigneDto::stockSuffisant));
    }

    /** Client parti chercher de quoi payer : panier conservé, stock réservé {@link #DUREE_ATTENTE}. */
    @Transactional
    public VenteDto mettreEnAttente(MiseEnAttenteCommande commande, String cle, UtilisateurConnecte vendeur) {
        Optional<Vente> deja = rejouer(cle, vendeur);
        if (deja.isPresent()) return versDto(deja.get());

        SessionOuverte session = sessions.sessionOuverte(vendeur.id());
        Vente reprise = reprendreVerrouillee(commande.panier().venteEnAttenteId(), session);
        Panier panier = preparer(commande.panier(), reprise);
        Long valideePar = autoriserRemise(panier, commande.panier(), vendeur, reprise);
        Instant maintenant = Instant.now(clock);
        if (reprise != null) stock.liberer(quantites(reprise));

        Vente vente = reprise != null ? reprise : nouvelle(session, cle, vendeur, maintenant);
        vente.remplirPanier(panier.calcul(), remiseTicket(commande.panier()), motif(commande.panier()), valideePar);
        vente.mettreEnAttente(texte(commande.note()), maintenant.plus(DUREE_ATTENTE));
        ventes.save(vente);
        stock.reserver(quantites(panier));
        return versDto(vente, panier.produits());
    }

    /** Validation et encaissement en espèces ; la monnaie à rendre est calculée ici. */
    @Transactional
    public VenteDto valider(ValidationCommande commande, String cle, UtilisateurConnecte vendeur) {
        Optional<Vente> deja = rejouer(cle, vendeur);
        if (deja.isPresent()) {
            if (deja.get().getStatut() != StatutVente.VALIDEE) {
                throw new ConflitException("Cette clé d'idempotence appartient à une autre opération.");
            }
            return versDto(deja.get());
        }

        SessionOuverte session = sessions.sessionOuverte(vendeur.id());
        Vente reprise = reprendreVerrouillee(commande.panier().venteEnAttenteId(), session);
        Panier panier = preparer(commande.panier(), reprise);
        Long valideePar = autoriserRemise(panier, commande.panier(), vendeur, reprise);
        Instant maintenant = Instant.now(clock);
        if (reprise != null) stock.liberer(quantites(reprise));

        Vente vente = reprise != null ? reprise : nouvelle(session, cle, vendeur, maintenant);
        vente.remplirPanier(panier.calcul(), remiseTicket(commande.panier()), motif(commande.panier()), valideePar);
        vente.validerEnEspeces(commande.especesRecues(), cle, maintenant);
        ventes.save(vente);
        stock.sortirPourVente(quantites(panier), vente.getNumero(), vendeur.id(), maintenant);
        if (valideePar != null) {
            journal.enregistrer(
                    vendeur.id(),
                    ActionJournal.REMISE_AUTORISEE,
                    "Vente " + vente.getNumero() + " : remise de "
                            + panier.calcul().tauxRemiseMax().toPlainString() + " % autorisée par l'utilisateur "
                            + valideePar + ". Motif : " + vente.getMotifRemise());
        }
        return versDto(vente, panier.produits());
    }

    /** Le client est revenu : la vente est rechargée sur la caisse et sa réservation prolongée. */
    @Transactional
    public VenteDto reprendre(long venteId, UtilisateurConnecte vendeur) {
        SessionOuverte session = sessions.sessionOuverte(vendeur.id());
        Vente vente = reprendreVerrouillee(venteId, session);
        vente.prolongerAttente(Instant.now(clock).plus(DUREE_ATTENTE));
        return versDto(vente);
    }

    /** Le client ne revient pas : la vente est abandonnée et son stock libéré. */
    @Transactional
    public VenteDto abandonner(long venteId, UtilisateurConnecte vendeur) {
        SessionOuverte session = sessions.sessionOuverte(vendeur.id());
        Vente vente = reprendreVerrouillee(venteId, session);
        stock.liberer(quantites(vente));
        vente.annulerAttente(Instant.now(clock));
        return versDto(vente);
    }

    /** Ventes en attente de la session ouverte du vendeur (vide sans session). */
    @Transactional(readOnly = true)
    public List<VenteDto> enAttente(UtilisateurConnecte vendeur) {
        return sessions.courante(vendeur.id())
                .map(s -> ventes.findBySessionCaisseIdAndStatutOrderByCreeLeAsc(s.id(), StatutVente.EN_ATTENTE))
                .map(this::versDtos)
                .orElse(List.of());
    }

    /** Échéance d'une vente en attente (tâche planifiée) ; sans effet si elle a été reprise entre-temps. */
    @Transactional
    public boolean annulerSiExpiree(long venteId) {
        Instant maintenant = Instant.now(clock);
        Vente vente = ventes.findForUpdate(venteId).orElse(null);
        if (vente == null || !vente.estExpiree(maintenant)) return false;
        stock.liberer(quantites(vente));
        vente.annulerAttente(maintenant);
        return true;
    }

    /** Clôture de session : les ventes encore en attente sont annulées dans la même transaction. */
    @EventListener
    public void annulerAttentesDeSession(SessionCaisseCloturee evenement) {
        Instant maintenant = Instant.now(clock);
        for (long id : ventes.findIdsEnAttente(evenement.sessionId())) {
            Vente vente = ventes.findForUpdate(id).orElseThrow();
            if (!vente.estEnAttente()) continue;
            stock.liberer(quantites(vente));
            vente.annulerAttente(maintenant);
        }
    }

    // ---------------------------------------------------------------- Préparation du panier

    /** Produits chargés en une requête, prix du catalogue (ou figés par la vente reprise) et calcul. */
    private Panier preparer(PanierCommande commande, Vente reprise) {
        Set<Long> ids = new HashSet<>();
        for (LigneCommande ligne : commande.lignes()) {
            if (!ids.add(ligne.produitId())) {
                throw new RegleMetierException("Un même produit apparaît sur deux lignes du panier.");
            }
        }
        Map<Long, Produit> parId =
                produits.findWithTvaByIdIn(ids).stream().collect(Collectors.toMap(Produit::getId, Function.identity()));
        Map<Long, LigneVente> figees = reprise == null
                ? Map.of()
                : reprise.getLignes().stream().collect(Collectors.toMap(LigneVente::getProduitId, Function.identity()));
        List<LignePanier> lignes = commande.lignes().stream()
                .map(l -> {
                    Produit produit = parId.get(l.produitId());
                    if (produit == null) throw new RessourceIntrouvableException("Produit introuvable.");
                    if (!produit.isActif()) {
                        throw new RegleMetierException(produit.getDesignation() + " n'est plus en vente.");
                    }
                    LigneVente figee = figees.get(l.produitId());
                    return new LignePanier(
                            produit.getId(),
                            l.quantite(),
                            figee != null ? figee.getPrixUnitaire() : produit.getPpv(),
                            figee != null
                                    ? figee.getTauxTva()
                                    : produit.getTva().getTaux(),
                            remise(l.remise()));
                })
                .toList();
        return new Panier(parId, CalculateurVente.calculer(lignes, remiseTicket(commande)));
    }

    private NiveauAutorisation niveauRequis(PanierCalcule calcul) {
        Parametres p = parametres.lire();
        return NiveauAutorisation.requis(
                calcul.tauxRemiseMax(), new PlafondsRemise(p.plafondRemiseCaissier(), p.plafondRemiseResponsable()));
    }

    /** Remise au-delà du plafond caissier : motif obligatoire et autorisation du bon niveau. */
    private Long autoriserRemise(Panier panier, PanierCommande commande, UtilisateurConnecte vendeur, Vente reprise) {
        NiveauAutorisation niveau = niveauRequis(panier.calcul());
        if (niveau == NiveauAutorisation.AUCUNE) return null;
        if (motif(commande) == null) {
            throw new RegleMetierException("Indiquez le motif de la remise.");
        }
        return valideePar(niveau, commande, vendeur, reprise);
    }

    /** Qui couvre le niveau exigé : le vendeur lui-même, le jeton joint, ou celui qui a validé la vente reprise. */
    private Long valideePar(
            NiveauAutorisation niveau, PanierCommande commande, UtilisateurConnecte vendeur, Vente reprise) {
        if (niveau.estCouvertPar(vendeur.role())) {
            return niveau == NiveauAutorisation.AUCUNE ? null : vendeur.id();
        }
        if (commande.jetonAutorisation() != null
                && !commande.jetonAutorisation().isBlank()) {
            Autorisation autorisation = autorisations.verifier(commande.jetonAutorisation(), vendeur.id());
            if (niveau.estCouvertPar(autorisation.role())) return autorisation.autorisateurId();
        } else if (reprise != null && reprise.getValideePar() != null) {
            Role role = utilisateurs
                    .findById(reprise.getValideePar())
                    .filter(Utilisateur::isActif)
                    .map(Utilisateur::getRole)
                    .orElse(Role.CAISSIER);
            if (niveau.estCouvertPar(role)) return reprise.getValideePar();
        }
        throw new OperationInterditeException(
                niveau == NiveauAutorisation.ADMIN
                        ? "Cette remise dépasse le plafond responsable : autorisation de l'admin requise."
                        : "Cette remise dépasse votre plafond : autorisation d'un responsable requise.");
    }

    // ---------------------------------------------------------------- Ventes en attente

    private Vente reprendreVerrouillee(Long venteId, SessionOuverte session) {
        if (venteId == null) return null;
        Vente vente = ventes.findForUpdate(venteId)
                .orElseThrow(() -> new RessourceIntrouvableException("Vente en attente introuvable."));
        if (!Objects.equals(vente.getCaisseId(), session.caisseId())
                || !Objects.equals(vente.getSessionCaisseId(), session.id())) {
            throw new OperationInterditeException("Une vente en attente se reprend uniquement sur sa caisse.");
        }
        if (!vente.estEnAttente()) {
            throw new ConflitException("Cette vente n'est plus en attente.");
        }
        if (vente.estExpiree(Instant.now(clock))) {
            throw new ConflitException("Cette vente en attente a expiré : son stock a été libéré.");
        }
        return vente;
    }

    private Optional<Vente> rejouer(String cle, UtilisateurConnecte vendeur) {
        Optional<Vente> vente = ventes.findByCleIdempotence(cle);
        if (vente.isPresent() && vente.get().getCaissierId() != vendeur.id()) {
            throw new ConflitException("Cette clé d'idempotence appartient à une autre opération.");
        }
        return vente;
    }

    private Vente nouvelle(SessionOuverte session, String cle, UtilisateurConnecte vendeur, Instant maintenant) {
        return new Vente(
                numeroteur.suivant(session.caisseId()),
                cle,
                session.caisseId(),
                vendeur.id(),
                session.id(),
                maintenant);
    }

    // ---------------------------------------------------------------- Conversions

    private static Remise remise(RemiseDto dto) {
        return dto == null ? null : Remise.ou(dto.type(), dto.valeur());
    }

    private static Remise remiseTicket(PanierCommande commande) {
        return remise(commande.remiseTicket());
    }

    private static String motif(PanierCommande commande) {
        return texte(commande.motifRemise());
    }

    private static String texte(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static List<Quantite> quantites(Panier panier) {
        return panier.calcul().lignes().stream()
                .map(l -> new Quantite(
                        l.ligne().produitId(),
                        panier.produits().get(l.ligne().produitId()).getDesignation(),
                        l.ligne().quantite()))
                .toList();
    }

    private static List<Quantite> quantites(Vente vente) {
        return vente.getLignes().stream()
                .map(l -> new Quantite(l.getProduitId(), "le produit " + l.getProduitId(), l.getQuantite()))
                .toList();
    }

    private static Map<Long, Integer> quantitesParProduit(Vente vente) {
        return vente.getLignes().stream().collect(Collectors.toMap(LigneVente::getProduitId, LigneVente::getQuantite));
    }

    private VenteDto versDto(Vente vente) {
        return versDtos(List.of(vente)).getFirst();
    }

    /** Désignations chargées en une requête pour toutes les ventes. */
    private List<VenteDto> versDtos(List<Vente> liste) {
        Set<Long> ids = liste.stream()
                .flatMap(v -> v.getLignes().stream())
                .map(LigneVente::getProduitId)
                .collect(Collectors.toSet());
        Map<Long, Produit> parId =
                produits.findAllById(ids).stream().collect(Collectors.toMap(Produit::getId, Function.identity()));
        return liste.stream().map(v -> versDto(v, parId)).toList();
    }

    private static VenteDto versDto(Vente vente, Map<Long, Produit> parId) {
        List<LignePanier> lignes = vente.getLignes().stream()
                .map(l -> new LignePanier(
                        l.getProduitId(), l.getQuantite(), l.getPrixUnitaire(), l.getTauxTva(), l.getRemise()))
                .toList();
        PanierCalcule calcul = CalculateurVente.calculer(lignes, vente.getRemiseTicket());
        var paiement = vente.getPaiements().stream()
                .findFirst()
                .map(p -> new PaiementDto(p.getMode(), p.getMontant(), p.getMontantRecu(), p.getRendu()))
                .orElse(null);
        return new VenteDto(
                vente.getId(),
                vente.getNumero(),
                vente.getStatut(),
                vente.getCaisseId(),
                vente.getCaissierId(),
                calcul.lignes().stream()
                        .map(l -> ligneDto(
                                l,
                                Optional.ofNullable(parId.get(l.ligne().produitId()))
                                        .map(Produit::getDesignation)
                                        .orElse("?"),
                                true))
                        .toList(),
                remiseDto(vente.getRemiseTicket()),
                totaux(calcul),
                vente.getMotifRemise(),
                vente.getValideePar(),
                vente.getNoteAttente(),
                vente.getAttenteExpireLe(),
                vente.getCreeLe(),
                vente.getFinaliseeLe(),
                paiement);
    }

    private static LigneDto ligneDto(LigneCalculee l, String designation, boolean stockSuffisant) {
        return new LigneDto(
                l.ligne().produitId(),
                designation,
                l.ligne().quantite(),
                l.ligne().prixUnitaire(),
                l.ligne().tauxTva(),
                remiseDto(l.ligne().remise()),
                l.montantBrut(),
                l.remiseMontant(),
                l.montantNet(),
                stockSuffisant);
    }

    private static RemiseDto remiseDto(Remise remise) {
        return remise == null ? null : new RemiseDto(remise.type(), remise.valeur());
    }

    private static TotauxDto totaux(PanierCalcule c) {
        return new TotauxDto(
                c.sousTotal(),
                c.remisesLignes(),
                c.remiseTicket(),
                c.totalRemises(),
                c.totalHt(),
                c.totalTva(),
                c.totalTtc(),
                c.tva().stream()
                        .map(t -> new VentilationTvaDto(t.taux(), t.totalHt(), t.totalTva(), t.totalTtc()))
                        .toList());
    }

    private record Panier(Map<Long, Produit> produits, PanierCalcule calcul) {}
}
