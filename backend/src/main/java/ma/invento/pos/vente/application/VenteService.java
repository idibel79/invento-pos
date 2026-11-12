package ma.invento.pos.vente.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import ma.invento.pos.caisse.application.SessionCaisseCloturee;
import ma.invento.pos.caisse.application.SessionCaisseService;
import ma.invento.pos.caisse.application.SessionCaisseService.SessionOuverte;
import ma.invento.pos.catalogue.domain.Produit;
import ma.invento.pos.catalogue.infrastructure.ProduitRepository;
import ma.invento.pos.client.application.ClientService;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.shared.domain.ConflitException;
import ma.invento.pos.shared.domain.OperationInterditeException;
import ma.invento.pos.shared.domain.RegleMetierException;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import ma.invento.pos.stock.application.StockService;
import ma.invento.pos.stock.application.StockService.Quantite;
import ma.invento.pos.vente.api.VenteDtos.ClientVenteDto;
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
import ma.invento.pos.vente.domain.ModePaiement;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vente au comptoir : aperçu du panier, validation en espèces, mise en attente avec stock réservé,
 * reprise sur la même caisse (qui libère la réservation), abandon et échéance. Chaque opération qui touche au stock tient
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
    private final AccordsRemise accordsRemise;
    private final NumeroteurVente numeroteur;
    private final JournalService journal;
    private final ClientService clients;
    private final Clock clock;

    public VenteService(
            VenteRepository ventes,
            ProduitRepository produits,
            UtilisateurRepository utilisateurs,
            SessionCaisseService sessions,
            StockService stock,
            AccordsRemise accordsRemise,
            NumeroteurVente numeroteur,
            JournalService journal,
            ClientService clients,
            Clock clock) {
        this.ventes = ventes;
        this.produits = produits;
        this.utilisateurs = utilisateurs;
        this.sessions = sessions;
        this.stock = stock;
        this.accordsRemise = accordsRemise;
        this.numeroteur = numeroteur;
        this.journal = journal;
        this.clients = clients;
        this.clock = clock;
    }

    /** Aperçu du panier : rien n'est enregistré ni réservé. */
    @Transactional(readOnly = true)
    public PanierDto calculer(PanierCommande commande, UtilisateurConnecte vendeur) {
        Vente reprise = commande.venteEnAttenteId() == null
                ? null
                : ventes.findById(commande.venteEnAttenteId())
                        .filter(v -> (v.estEnAttente() || v.estReprise()) && surSessionCourante(v, vendeur))
                        .orElseThrow(() -> new RessourceIntrouvableException("Vente en attente introuvable."));
        Panier panier = preparer(commande, reprise);
        AccordsRemise.Accords accords = accordsRemise.accorder(panier.calcul(), commande, vendeur, reprise);
        Map<Long, Integer> reservees =
                reprise == null || !reprise.estEnAttente() ? Map.of() : quantitesParProduit(reprise);
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
                accords.niveau(),
                accords.acquis(),
                lignes.stream().allMatch(LigneDto::stockSuffisant),
                accords.versDto());
    }

    /** Client parti chercher de quoi payer : panier conservé, stock réservé {@link #DUREE_ATTENTE}. */
    @Transactional
    public VenteDto mettreEnAttente(MiseEnAttenteCommande commande, String cle, UtilisateurConnecte vendeur) {
        Optional<Vente> deja = rejouer(cle, vendeur);
        if (deja.isPresent()) {
            if (deja.get().getStatut() != StatutVente.EN_ATTENTE) {
                throw new ConflitException("Cette clé d'idempotence appartient à une autre opération.");
            }
            return versDto(deja.get());
        }

        SessionOuverte session = sessions.sessionOuverte(vendeur.id());
        Vente reprise = reprendreVerrouillee(commande.panier().venteEnAttenteId(), session);
        Panier panier = preparer(commande.panier(), reprise);
        AccordsRemise.Accords accords = exigerAccords(panier, commande.panier(), vendeur, reprise);
        Instant maintenant = Instant.now(clock);
        if (reprise != null && reprise.estEnAttente()) stock.liberer(quantites(reprise));

        Vente vente = reprise != null ? reprise : nouvelle(session, cle, vendeur, maintenant);
        vente.remplirPanier(
                panier.calcul(),
                designations(panier),
                remiseTicket(commande.panier()),
                motif(commande.panier()),
                accords.principal(),
                accords.parArticle());
        vente.mettreEnAttente(texte(commande.note()), maintenant.plus(DUREE_ATTENTE));
        ventes.save(vente);
        stock.reserver(quantites(panier));
        return versDto(vente);
    }

    /**
     * Validation et encaissement (espèces, carte, les deux, ou crédit d'un client de l’annuaire) ; la monnaie
     * à rendre est calculée ici.
     */
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
        AccordsRemise.Accords accords = exigerAccords(panier, commande.panier(), vendeur, reprise);
        Instant maintenant = Instant.now(clock);
        if (reprise != null && reprise.estEnAttente()) stock.liberer(quantites(reprise));

        Vente vente = reprise != null ? reprise : nouvelle(session, cle, vendeur, maintenant);
        vente.remplirPanier(
                panier.calcul(),
                designations(panier),
                remiseTicket(commande.panier()),
                motif(commande.panier()),
                accords.principal(),
                accords.parArticle());
        ClientService.Dette dette = null;
        if (commande.mode() == ModePaiement.CREDIT) {
            if (commande.clientId() == null) throw new RegleMetierException("Choisissez le client qui paie à crédit.");
            dette = clients.vendreACredit(commande.clientId(), vente.getTotalTtc());
            vente.associerClient(commande.clientId(), dette.nom(), dette.apres());
        } else if (commande.clientId() != null) {
            vente.associerClient(commande.clientId(), clients.nomPourVente(commande.clientId()), null);
        }
        if (commande.mode() == ModePaiement.CREDIT) {
            vente.validerACredit(cle, maintenant);
        } else if (commande.mode() == ModePaiement.CARTE && commande.especesRecues() != null) {
            vente.validerEspecesEtCarte(commande.especesRecues(), cle, maintenant);
        } else if (commande.mode() == ModePaiement.CARTE) {
            vente.validerParCarte(cle, maintenant);
        } else if (commande.especesRecues() == null) {
            throw new RegleMetierException("Indiquez le montant reçu en espèces.");
        } else {
            // Avant la validation : la vente n'est pas encore comptée dans les espèces du tiroir.
            verifierMonnaie(commande.especesRecues().subtract(vente.getTotalTtc()), session);
            vente.validerEnEspeces(commande.especesRecues(), cle, maintenant);
        }
        if (dette != null) {
            // Le relevé du client référence la vente : elle doit être en base avant.
            ventes.saveAndFlush(vente);
            clients.inscrireVenteACredit(
                    commande.clientId(), vente.getId(), vente.getTotalTtc(), dette.apres(), vendeur.id(), session.id());
        } else {
            ventes.save(vente);
        }
        stock.sortirPourVente(quantites(panier), vente.getNumero(), vendeur.id(), maintenant);
        journaliserAccords(accords, vente, vendeur);
        return versDto(vente);
    }

    /**
     * La monnaie rendue sort des espèces déjà dans le tiroir (le billet du client ne peut pas servir à se
     * rendre la monnaie à lui-même) : elle ne peut pas les dépasser.
     */
    private void verifierMonnaie(BigDecimal rendu, SessionOuverte session) {
        if (rendu.signum() <= 0) return;
        BigDecimal disponibles = sessions.especesDisponibles(session.id());
        if (rendu.compareTo(disponibles) > 0) {
            throw new RegleMetierException("Monnaie insuffisante dans le tiroir : "
                    + disponibles.max(BigDecimal.ZERO).toPlainString() + " DH disponibles.");
        }
    }

    /**
     * Le client est revenu : la vente est rechargée dans le ticket, quitte l'attente et libère son
     * stock. Elle garde ses prix et sa remise autorisée jusqu'à l'encaissement ; sans encaissement,
     * elle est annulée quand le ticket est vidé ou à la clôture.
     */
    @Transactional
    public VenteDto reprendre(long venteId, UtilisateurConnecte vendeur) {
        SessionOuverte session = sessions.sessionOuverte(vendeur.id());
        Vente vente = reprendreVerrouillee(venteId, session);
        if (vente.estEnAttente()) {
            stock.liberer(quantites(vente));
            vente.reprendre();
        }
        return versDto(vente);
    }

    /** Le client ne revient pas (ou le ticket repris est vidé) : la vente est abandonnée, son stock libéré. */
    @Transactional
    public VenteDto abandonner(long venteId, UtilisateurConnecte vendeur) {
        SessionOuverte session = sessions.sessionOuverte(vendeur.id());
        Vente vente = reprendreVerrouillee(venteId, session);
        if (vente.estEnAttente()) stock.liberer(quantites(vente));
        vente.annulerAttente(Instant.now(clock));
        return versDto(vente);
    }

    /**
     * Vente validée telle qu'elle a été encaissée, pour réimprimer son ticket. Hors admin, seulement une
     * vente de l'utilisateur : celle d'un autre est introuvable pour lui.
     */
    @Transactional(readOnly = true)
    public VenteDto venteValidee(long venteId, UtilisateurConnecte utilisateur) {
        return ventes.findById(venteId)
                .filter(v -> v.getStatut() == StatutVente.VALIDEE)
                .filter(v -> utilisateur.estAdmin() || v.getCaissierId() == utilisateur.id())
                .map(VenteService::versDto)
                .orElseThrow(() -> new RessourceIntrouvableException("Vente introuvable."));
    }

    /** Vente validée avec un client de l'annuaire, à facturer ; quel que soit son vendeur. */
    @Transactional(propagation = Propagation.MANDATORY)
    public VenteDto venteAFacturer(long venteId) {
        VenteDto vente = ventes.findById(venteId)
                .filter(v -> v.getStatut() == StatutVente.VALIDEE)
                .map(VenteService::versDto)
                .orElseThrow(() -> new RessourceIntrouvableException("Vente introuvable."));
        if (vente.client() == null) {
            throw new RegleMetierException("Choisissez un client sur la vente pour imprimer une facture.");
        }
        return vente;
    }

    /** Ventes en attente de la session ouverte du vendeur (vide sans session). */
    @Transactional(readOnly = true)
    public List<VenteDto> enAttente(UtilisateurConnecte vendeur) {
        return sessions.courante(vendeur.id())
                .map(s -> ventes.findBySessionCaisseIdAndStatutOrderByCreeLeAsc(s.id(), StatutVente.EN_ATTENTE))
                .map(l -> l.stream().map(VenteService::versDto).toList())
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

    /** Clôture de session : les ventes en attente ou reprises non encaissées sont annulées dans la même transaction. */
    @EventListener
    public void annulerAttentesDeSession(SessionCaisseCloturee evenement) {
        Instant maintenant = Instant.now(clock);
        List<Vente> annulees = new ArrayList<>();
        for (long id : ventes.findIdsNonFinalisees(evenement.sessionId())) {
            Vente vente = ventes.findForUpdate(id).orElseThrow();
            if (vente.estEnAttente() || vente.estReprise()) annulees.add(vente);
        }
        // Une seule libération (seules les attentes réservent du stock), triée par produit comme les
        // autres opérations de stock (pas d'interblocage avec une vente concurrente sur une autre caisse).
        stock.liberer(annulees.stream()
                .filter(Vente::estEnAttente)
                .flatMap(v -> quantites(v).stream())
                .toList());
        annulees.forEach(v -> v.annulerAttente(maintenant));
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

    /** Remises accordées : motif obligatoire dès qu'une autorisation est demandée, sinon la vente est refusée. */
    private AccordsRemise.Accords exigerAccords(
            Panier panier, PanierCommande commande, UtilisateurConnecte vendeur, Vente reprise) {
        AccordsRemise.Accords accords = accordsRemise.accorder(panier.calcul(), commande, vendeur, reprise);
        if (accords.niveau() != NiveauAutorisation.AUCUNE && motif(commande) == null) {
            throw new RegleMetierException("Indiquez le motif de la remise.");
        }
        accords.exiger();
        return accords;
    }

    /** Une entrée au journal par remise accordée par un autre que le vendeur. */
    private void journaliserAccords(AccordsRemise.Accords accords, Vente vente, UtilisateurConnecte vendeur) {
        accords.tous()
                .filter(a -> a.valideePar() != null && a.valideePar() != vendeur.id())
                .forEach(a -> journal.enregistrer(
                        vendeur.id(),
                        ActionJournal.REMISE_AUTORISEE,
                        "Vente " + vente.getNumero() + " : remise de "
                                + a.taux().toPlainString() + " %"
                                + (a.produitId() == null ? " sur le ticket" : " sur l'article " + a.produitId())
                                + " autorisée par l'utilisateur " + a.valideePar() + ". Motif : "
                                + vente.getMotifRemise()));
    }

    // ---------------------------------------------------------------- Ventes en attente

    /** Vente de la session ouverte du vendeur, sur sa caisse (aperçu, sans verrou). */
    private boolean surSessionCourante(Vente vente, UtilisateurConnecte vendeur) {
        return sessions.courante(vendeur.id())
                .filter(s -> Objects.equals(vente.getCaisseId(), s.caisseId())
                        && Objects.equals(vente.getSessionCaisseId(), s.id()))
                .isPresent();
    }

    private Vente reprendreVerrouillee(Long venteId, SessionOuverte session) {
        if (venteId == null) return null;
        Vente vente = ventes.findForUpdate(venteId)
                .orElseThrow(() -> new RessourceIntrouvableException("Vente en attente introuvable."));
        if (!Objects.equals(vente.getCaisseId(), session.caisseId())
                || !Objects.equals(vente.getSessionCaisseId(), session.id())) {
            throw new OperationInterditeException("Une vente en attente se reprend uniquement sur sa caisse.");
        }
        if (!vente.estEnAttente() && !vente.estReprise()) {
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
                .map(l -> new Quantite(l.getProduitId(), l.getDesignation(), l.getQuantite()))
                .toList();
    }

    private static Map<Long, Integer> quantitesParProduit(Vente vente) {
        return vente.getLignes().stream().collect(Collectors.toMap(LigneVente::getProduitId, LigneVente::getQuantite));
    }

    /** Désignation actuelle de chaque produit du panier, figée sur les lignes de la vente. */
    private static Map<Long, String> designations(Panier panier) {
        return panier.produits().values().stream().collect(Collectors.toMap(Produit::getId, Produit::getDesignation));
    }

    /** Les lignes de la vente sont dans l'ordre de son calcul : la désignation figée suit le même index. */
    private static VenteDto versDto(Vente vente) {
        List<LignePanier> lignes = vente.getLignes().stream()
                .map(l -> new LignePanier(
                        l.getProduitId(), l.getQuantite(), l.getPrixUnitaire(), l.getTauxTva(), l.getRemise()))
                .toList();
        PanierCalcule calcul = CalculateurVente.calculer(lignes, vente.getRemiseTicket());
        List<PaiementDto> paiements = vente.getPaiements().stream()
                .map(p -> new PaiementDto(p.getMode(), p.getMontant(), p.getMontantRecu(), p.getRendu()))
                .toList();
        return new VenteDto(
                vente.getId(),
                vente.getNumero(),
                vente.getStatut(),
                vente.getCaisseId(),
                vente.getCaissierId(),
                IntStream.range(0, calcul.lignes().size())
                        .mapToObj(i -> ligneDto(
                                calcul.lignes().get(i), vente.getLignes().get(i).getDesignation(), true))
                        .toList(),
                remiseDto(vente.getRemiseTicket()),
                totaux(calcul),
                vente.getMotifRemise(),
                vente.getValideePar(),
                vente.getNoteAttente(),
                vente.getAttenteExpireLe(),
                vente.getCreeLe(),
                vente.getFinaliseeLe(),
                paiements.isEmpty() ? null : paiements.getFirst(),
                paiements,
                vente.getClientId() == null
                        ? null
                        : new ClientVenteDto(vente.getClientId(), vente.getClientNom(), vente.getDetteClientApres()));
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
