package ma.invento.pos.tresorerie.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.invento.pos.caisse.application.ComptesCaissiers;
import ma.invento.pos.caisse.application.EncaissementsSession;
import ma.invento.pos.caisse.application.EncaissementsSession.VentesSession;
import ma.invento.pos.caisse.domain.Caisse;
import ma.invento.pos.caisse.domain.ComptageTiroir;
import ma.invento.pos.caisse.domain.SessionCaisse;
import ma.invento.pos.caisse.infrastructure.CaisseRepository;
import ma.invento.pos.caisse.infrastructure.SessionCaisseRepository;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.parametre.application.ParametreService;
import ma.invento.pos.shared.api.PageResponse;
import ma.invento.pos.shared.domain.ConflitException;
import ma.invento.pos.shared.domain.OperationInterditeException;
import ma.invento.pos.shared.domain.RegleMetierException;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import ma.invento.pos.tresorerie.api.TresorerieDtos.CompteCaissierDto;
import ma.invento.pos.tresorerie.api.TresorerieDtos.ComptesDto;
import ma.invento.pos.tresorerie.api.TresorerieDtos.MonCompteDto;
import ma.invento.pos.tresorerie.api.TresorerieDtos.VersementDto;
import ma.invento.pos.tresorerie.domain.DuParMode;
import ma.invento.pos.tresorerie.domain.MouvementTpe;
import ma.invento.pos.tresorerie.domain.PlafondsVersement;
import ma.invento.pos.tresorerie.domain.ReglesTresorerie;
import ma.invento.pos.tresorerie.domain.StatutVersement;
import ma.invento.pos.tresorerie.domain.TypeCompte;
import ma.invento.pos.tresorerie.domain.TypeOperation;
import ma.invento.pos.tresorerie.infrastructure.ComptesJdbc;
import ma.invento.pos.tresorerie.infrastructure.ComptesJdbc.Compte;
import ma.invento.pos.tresorerie.infrastructure.JournalTresorerieJdbc;
import ma.invento.pos.tresorerie.infrastructure.JournalTresorerieJdbc.Filtre;
import ma.invento.pos.tresorerie.infrastructure.JournalTresorerieJdbc.NouvelleOperation;
import ma.invento.pos.tresorerie.infrastructure.JournalTresorerieJdbc.Operation;
import ma.invento.pos.tresorerie.infrastructure.VersementsJdbc;
import ma.invento.pos.tresorerie.infrastructure.VersementsJdbc.EnTransit;
import ma.invento.pos.tresorerie.infrastructure.VersementsJdbc.NouveauVersement;
import ma.invento.pos.tresorerie.infrastructure.VersementsJdbc.Versement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caisse principale (admin) et comptes des caissiers. Le solde d'un caissier est ce qu'il doit à la
 * caisse principale : solde stocké + ventes de sa session ouverte (reportées sur le solde stocké à la
 * clôture). Chaque mouvement de solde est inscrit au journal de la caisse principale.
 */
@Service
public class TresorerieService implements ComptesCaissiers {

    private final ComptesJdbc comptes;
    private final VersementsJdbc versements;
    private final JournalTresorerieJdbc operations;
    private final SessionCaisseRepository sessions;
    private final CaisseRepository caisses;
    private final EncaissementsSession encaissements;
    private final RemboursementsDettes remboursements;
    private final UtilisateurRepository utilisateurs;
    private final ParametreService parametres;
    private final JournalService journal;
    private final Clock clock;

    public TresorerieService(
            ComptesJdbc comptes,
            VersementsJdbc versements,
            JournalTresorerieJdbc operations,
            SessionCaisseRepository sessions,
            CaisseRepository caisses,
            EncaissementsSession encaissements,
            RemboursementsDettes remboursements,
            UtilisateurRepository utilisateurs,
            ParametreService parametres,
            JournalService journal,
            Clock clock) {
        this.comptes = comptes;
        this.versements = versements;
        this.operations = operations;
        this.sessions = sessions;
        this.caisses = caisses;
        this.encaissements = encaissements;
        this.remboursements = remboursements;
        this.utilisateurs = utilisateurs;
        this.parametres = parametres;
        this.journal = journal;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- Sessions de caisse

    @Override
    @Transactional(readOnly = true)
    public BigDecimal solde(long utilisateurId) {
        return soldeAffiche(utilisateurId);
    }

    @Override
    @Transactional(readOnly = true)
    public MouvementsSession mouvementsSession(long sessionId) {
        BigDecimal[] recuEtVerse = versements.recuEtVerse(sessionId);
        RemboursementsDettes.Montants dettes = remboursements.pendantLaSession(sessionId);
        return new MouvementsSession(
                recuEtVerse[0].add(dettes.especes()), dettes.tpe(), recuEtVerse[1], recuEtVerse[2], dettes.especes());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cloturer(SessionCaisse session, BigDecimal ventes, long auteurId) {
        Compte compte = comptes.caissierOuCree(session.getCaissierId());
        BigDecimal credit = ComptageTiroir.creditCloture(ventes, session.getEcart());
        if (credit.signum() > 0) comptes.crediter(compte.id(), credit);
        journaliser(
                TypeOperation.CLOTURE_SESSION,
                compte.id(),
                credit,
                compte.utilisateurId(),
                session,
                null,
                session.getEcart(),
                null,
                auteurId,
                null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cloturerDOffice(SessionCaisse session, BigDecimal ventes, long adminId) {
        Compte compte = comptes.caissierOuCree(session.getCaissierId());
        if (ventes.signum() > 0) comptes.crediter(compte.id(), ventes);
        journaliser(
                TypeOperation.CLOTURE_SESSION,
                compte.id(),
                ventes,
                compte.utilisateurId(),
                session,
                null,
                null,
                "Clôture d'office (caisse désactivée), comptage du tiroir en attente",
                adminId,
                null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void terminerComptage(SessionCaisse session, BigDecimal montantVerse, long adminId) {
        Compte compte = comptes.caissierOuCree(session.getCaissierId());
        BigDecimal excedent = session.getEcart().max(BigDecimal.ZERO);
        if (excedent.signum() > 0) comptes.crediter(compte.id(), excedent);
        journaliser(
                TypeOperation.CLOTURE_SESSION,
                compte.id(),
                excedent,
                compte.utilisateurId(),
                session,
                null,
                session.getEcart(),
                "Tiroir compté par l'admin",
                adminId,
                null);
        if (montantVerse.signum() > 0) {
            // L'admin a le tiroir en main : le versement entre directement dans la caisse principale.
            Versement versement = envoyer(
                    compte,
                    comptes.principale(),
                    montantVerse,
                    null,
                    null,
                    session,
                    true,
                    BigDecimal.ZERO,
                    null,
                    adminId,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO);
            valider(versement, adminId, false, null);
        }
    }

    // ---------------------------------------------------------------- Compte de l'utilisateur connecté

    @Transactional(readOnly = true)
    public MonCompteDto monCompte(long utilisateurId) {
        Optional<Compte> compte = comptes.caissier(utilisateurId);
        Optional<SessionCaisse> session = sessions.findByCaissierIdAndFermetureIsNull(utilisateurId);
        BigDecimal attendu =
                session.map(s -> tiroir(s).attendu(s.getFondInitial())).orElse(null);
        List<VersementDto> aValider =
                compte.map(c -> dtos(versements.enTransitVers(c.id()))).orElse(List.of());
        List<VersementDto> historique = compte.map(c -> dtos(versements.recentsDuCompte(c.id(), debutDuJour())))
                .orElse(List.of());
        DuParMode du = du(utilisateurId);
        PlafondsVersement plafonds = plafonds(utilisateurId, session.orElse(null));
        return new MonCompteDto(
                du.total(),
                du.especes(),
                du.tpe(),
                session.isPresent(),
                attendu,
                plafonds.tiroirEspeces(),
                plafonds.tiroirTpe(),
                plafonds.compteEspeces(),
                plafonds.compteTpe(),
                aValider,
                historique);
    }

    /**
     * Versement du caissier vers la caisse principale, à tout moment : en espèces et en tickets TPE, comme le
     * paiement d'un ticket ; le total est débité de son compte. Caisse ouverte, {@code tiroirEspeces} et
     * {@code tiroirTpe} sortent du tiroir (ventes de la session comprises) et le reste vient de l'argent qu'il
     * garde sur lui ; chaque part reste dans son plafond.
     */
    @Transactional
    public VersementDto verserALaPrincipale(
            UtilisateurConnecte utilisateur,
            BigDecimal especes,
            BigDecimal tpe,
            BigDecimal tiroirEspeces,
            BigDecimal tiroirTpe,
            UUID cle) {
        Repartition repartition = new Repartition(especes, tpe);
        BigDecimal montant = repartition.total();
        ReglesTresorerie.verifierMontant(montant);
        BigDecimal duTiroirEspeces = tiroirEspeces == null ? BigDecimal.ZERO : tiroirEspeces;
        BigDecimal duTiroirTpe = tiroirTpe == null ? BigDecimal.ZERO : tiroirTpe;
        if (duTiroirEspeces.compareTo(especes) > 0 || duTiroirTpe.compareTo(tpe) > 0) {
            throw new RegleMetierException("La part versée depuis le tiroir dépasse le montant du versement.");
        }
        Optional<VersementDto> deja = dejaEnvoye(cle, utilisateur.id());
        if (deja.isPresent()) return deja.get();
        Compte compte = comptes.caissierOuCree(utilisateur.id());
        SessionCaisse session =
                sessions.findOuverteDuCaissierForUpdate(utilisateur.id()).orElse(null);
        comptes.verrouiller(compte.id());
        plafonds(utilisateur.id(), session)
                .verifier(duTiroirEspeces, duTiroirTpe, especes.subtract(duTiroirEspeces), tpe.subtract(duTiroirTpe));
        // Les ventes de la session n'entrent au solde qu'à la clôture : verser depuis le tiroir peut le faire
        // passer provisoirement sous zéro, au plus de ces ventes.
        BigDecimal plancher =
                session == null ? BigDecimal.ZERO : tiroir(session).ventes().negate();
        Versement versement = envoyer(
                compte,
                comptes.principale(),
                montant,
                repartition,
                null,
                session,
                false,
                plancher,
                cle,
                utilisateur.id(),
                duTiroirEspeces,
                duTiroirTpe);
        // L'admin qui est aussi caissier valide lui-même ses versements (auto-validation au journal).
        if (utilisateur.estAdmin()) versement = valider(versement, utilisateur.id(), true, null);
        return VersementDto.depuis(versement);
    }

    /**
     * Confirmation par le récepteur qu'il a reçu l'argent : il est crédité. Un caissier dont la caisse est
     * ouverte met {@code tiroir} dans son tiroir et garde le reste sur lui.
     */
    @Transactional
    public VersementDto valider(long versementId, BigDecimal tiroir, UtilisateurConnecte utilisateur) {
        Versement versement = chargerPourMaj(versementId);
        verifierRecepteur(versement, utilisateur);
        versement.statut().verifierEnTransit();
        if (tiroir != null && (tiroir.signum() < 0 || tiroir.compareTo(versement.montant()) > 0)) {
            throw new RegleMetierException(
                    "La part mise dans le tiroir doit être comprise entre 0 et le montant reçu.");
        }
        return VersementDto.depuis(valider(versement, utilisateur.id(), false, tiroir));
    }

    /** Refus par le récepteur, avec un motif : l'argent revient à l'émetteur. */
    @Transactional
    public VersementDto refuser(long versementId, String motif, UtilisateurConnecte utilisateur) {
        String propre = ReglesTresorerie.motif(motif, true);
        Versement versement = chargerPourMaj(versementId);
        verifierRecepteur(versement, utilisateur);
        versement.statut().verifierEnTransit();
        return VersementDto.depuis(
                rendre(versement, StatutVersement.REFUSE, propre, utilisateur.id(), TypeOperation.VERSEMENT_REFUSE));
    }

    /** Annulation par l'émetteur tant que le versement n'est pas validé : l'argent lui revient. */
    @Transactional
    public VersementDto annuler(long versementId, UtilisateurConnecte utilisateur) {
        Versement versement = chargerPourMaj(versementId);
        boolean emetteur =
                versement.versLaPrincipale() ? versement.caissierId() == utilisateur.id() : utilisateur.estAdmin();
        if (!emetteur) throw new OperationInterditeException("Seul l'émetteur peut annuler ce versement.");
        versement.statut().verifierEnTransit();
        return VersementDto.depuis(
                rendre(versement, StatutVersement.ANNULE, null, utilisateur.id(), TypeOperation.VERSEMENT_ANNULE));
    }

    // ---------------------------------------------------------------- Caisse principale (admin)

    @Transactional
    public void alimenter(BigDecimal montant, String motif, UUID cle, long adminId) {
        ReglesTresorerie.verifierMontant(montant);
        String propre = ReglesTresorerie.motif(motif, false);
        if (cle != null && operations.existeCle(cle)) return;
        Compte principale = comptes.principale();
        comptes.crediter(principale.id(), montant);
        journaliser(TypeOperation.ALIMENTATION, principale.id(), montant, null, null, null, null, propre, adminId, cle);
        journal.enregistrer(
                adminId,
                ActionJournal.CAISSE_PRINCIPALE_ALIMENTEE,
                montant.toPlainString() + " DH" + (propre == null ? "" : " : " + propre));
    }

    @Transactional
    public void retirer(BigDecimal montant, String motif, UUID cle, long adminId) {
        ReglesTresorerie.verifierMontant(montant);
        String propre = ReglesTresorerie.motif(motif, true);
        if (cle != null && operations.existeCle(cle)) return;
        Compte principale = comptes.principale();
        comptes.debiter(principale.id(), montant, BigDecimal.ZERO)
                .orElseThrow(() -> soldeInsuffisant("de la caisse principale", principale.solde()));
        journaliser(
                TypeOperation.RETRAIT, principale.id(), montant.negate(), null, null, null, null, propre, adminId, cle);
        journal.enregistrer(
                adminId, ActionJournal.CAISSE_PRINCIPALE_RETRAIT, montant.toPlainString() + " DH : " + propre);
    }

    /** Versement de la caisse principale à un caissier : en transit jusqu'à ce qu'il confirme l'avoir reçu. */
    @Transactional
    public VersementDto verserAuCaissier(long utilisateurId, BigDecimal montant, String motif, UUID cle, long adminId) {
        ReglesTresorerie.verifierMontant(montant);
        String propre = ReglesTresorerie.motif(motif, false);
        Optional<VersementDto> deja = dejaEnvoye(cle, adminId);
        if (deja.isPresent()) return deja.get();
        Utilisateur caissier = chargerUtilisateur(utilisateurId);
        if (!caissier.isActif()) throw new RegleMetierException("Cet utilisateur est désactivé.");
        Versement versement = envoyer(
                comptes.principale(),
                comptes.caissierOuCree(utilisateurId),
                montant,
                null,
                propre,
                null,
                false,
                BigDecimal.ZERO,
                cle,
                adminId,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
        if (utilisateurId == adminId) versement = valider(versement, adminId, true, null);
        return VersementDto.depuis(versement);
    }

    /**
     * Argent remis en main propre à l'admin par un utilisateur désactivé, qui ne peut plus verser lui-même :
     * versement à la caisse principale enregistré par l'admin et reçu aussitôt, dans la limite du dû par mode.
     */
    @Transactional
    public VersementDto recevoirDe(long utilisateurId, BigDecimal especes, BigDecimal tpe, UUID cle, long adminId) {
        Repartition repartition = new Repartition(especes, tpe);
        ReglesTresorerie.verifierMontant(repartition.total());
        Optional<VersementDto> deja = dejaEnvoye(cle, adminId);
        if (deja.isPresent()) return deja.get();
        Utilisateur caissier = chargerUtilisateur(utilisateurId);
        if (caissier.isActif()) {
            throw new RegleMetierException("Un utilisateur actif verse lui-même son argent depuis Mon compte.");
        }
        if (sessions.findOuverteDuCaissierForUpdate(utilisateurId).isPresent()) {
            throw new RegleMetierException("Sa caisse est encore ouverte : clôturez-la d'office avant.");
        }
        Compte compte = comptes.caissierOuCree(utilisateurId);
        comptes.verrouiller(compte.id());
        DuParMode du = du(utilisateurId);
        plafond(especes, du.especes().max(BigDecimal.ZERO), "en espèces");
        plafond(tpe, du.tpe(), "en tickets TPE");
        Versement versement = envoyer(
                compte,
                comptes.principale(),
                repartition.total(),
                repartition,
                null,
                null,
                false,
                BigDecimal.ZERO,
                cle,
                adminId,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
        return VersementDto.depuis(valider(versement, adminId, false, null));
    }

    private static void plafond(BigDecimal montant, BigDecimal du, String mode) {
        if (montant.compareTo(du) > 0) {
            throw new RegleMetierException(
                    "Il ne doit que " + du.toPlainString() + " DH " + mode + " à la caisse principale.");
        }
    }

    /** Passage en perte : le solde du caissier baisse, sans effet sur la caisse principale. */
    @Transactional
    public void ajuster(long utilisateurId, BigDecimal montant, String motif, UUID cle, long adminId) {
        ReglesTresorerie.verifierMontant(montant);
        String propre = ReglesTresorerie.motif(motif, true);
        if (cle != null && operations.existeCle(cle)) return;
        chargerUtilisateur(utilisateurId);
        Compte compte = comptes.caissierOuCree(utilisateurId);
        Optional<SessionCaisse> session = sessions.findOuverteDuCaissierForUpdate(utilisateurId);
        comptes.debiter(compte.id(), montant, BigDecimal.ZERO)
                .orElseThrow(() -> soldeInsuffisant("du caissier", soldeAffiche(utilisateurId)));
        journaliser(
                TypeOperation.AJUSTEMENT,
                compte.id(),
                montant.negate(),
                utilisateurId,
                session.orElse(null),
                null,
                null,
                propre,
                adminId,
                cle);
        journal.enregistrer(
                adminId,
                ActionJournal.COMPTE_AJUSTE,
                "Utilisateur " + utilisateurId + " : −" + montant.toPlainString() + " DH, " + propre);
    }

    @Transactional(readOnly = true)
    public ComptesDto comptes() {
        Compte principale = comptes.principale();
        List<Versement> aValider = versements.enTransitVers(principale.id());
        Map<Long, BigDecimal> soldes = comptes.soldesCaissiers();
        List<Utilisateur> tous = utilisateurs.findAllByOrderByNomAscPrenomAsc();
        Map<Long, List<MouvementTpe>> mouvementsTpe =
                versements.mouvementsTpe(tous.stream().map(Utilisateur::getId).toList(), false);
        Map<Long, EnTransit> enTransit = versements.enTransitParCaissier();
        Map<Long, SessionCaisse> ouvertes = sessions.findByFermetureIsNull().stream()
                .collect(Collectors.toMap(SessionCaisse::getCaissierId, Function.identity()));
        Map<Long, String> nomsCaisses =
                caisses
                        .findAllById(ouvertes.values().stream()
                                .map(SessionCaisse::getCaisseId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Caisse::getId, Caisse::getNom));
        Map<Long, VentesSession> ventesOuvertes = encaissements.ventesParSession(
                ouvertes.values().stream().map(SessionCaisse::getId).toList());
        List<CompteCaissierDto> caissiers = tous.stream()
                .map(u -> {
                    SessionCaisse session = ouvertes.get(u.getId());
                    EnTransit transit = enTransit.getOrDefault(u.getId(), EnTransit.AUCUN);
                    // Les ventes d'une session ouverte n'entrent dans le solde dû qu'à sa clôture.
                    DuParMode du = DuParMode.depuis(
                            soldes.getOrDefault(u.getId(), BigDecimal.ZERO),
                            MouvementTpe.nonVerses(mouvementsTpe.getOrDefault(u.getId(), List.of())));
                    return new CompteCaissierDto(
                            u.getId(),
                            (u.getPrenom() + " " + u.getNom()).trim(),
                            u.getRole(),
                            u.isActif(),
                            du.total(),
                            du.especes(),
                            du.tpe(),
                            session == null ? null : nomsCaisses.get(session.getCaisseId()),
                            session == null
                                    ? null
                                    : ventesOuvertes
                                            .getOrDefault(session.getId(), VentesSession.AUCUNE)
                                            .total(),
                            transit.recusNombre(),
                            transit.recusMontant(),
                            transit.envoyesNombre(),
                            transit.envoyesMontant());
                })
                .toList();
        return new ComptesDto(
                principale.solde(),
                aValider.size(),
                aValider.stream().map(Versement::montant).reduce(BigDecimal.ZERO, BigDecimal::add),
                caissiers);
    }

    /** Versements en transit vers la caisse principale, à valider par l'admin. */
    @Transactional(readOnly = true)
    public List<VersementDto> aValiderPrincipale() {
        return dtos(versements.enTransitVers(comptes.principale().id()));
    }

    /** Versements en cours ou du jour d'un caissier, vus par l'admin. */
    @Transactional(readOnly = true)
    public List<VersementDto> versementsDuCaissier(long utilisateurId) {
        return comptes.caissier(utilisateurId)
                .map(c -> dtos(versements.recentsDuCompte(c.id(), debutDuJour())))
                .orElse(List.of());
    }

    /** Journal de la caisse principale ; un jour s'entend dans le fuseau de la boutique. */
    @Transactional(readOnly = true)
    public PageResponse<Operation> journal(
            LocalDate jour,
            Long caisseId,
            Long utilisateurId,
            JournalTresorerieJdbc.FiltreStatut statut,
            JournalTresorerieJdbc.FiltreType type,
            boolean croissant,
            int page,
            int taille) {
        // Sans utilisateur : les seules opérations de la caisse principale. Avec : tout le compte de cet
        // utilisateur, clôtures (ventes) et ajustements compris, vu de son compte.
        Set<TypeOperation> types = utilisateurId == null
                ? Set.of(
                        TypeOperation.ALIMENTATION,
                        TypeOperation.RETRAIT,
                        TypeOperation.VERSEMENT_ENVOYE,
                        TypeOperation.REMBOURSEMENT_DETTE,
                        TypeOperation.ANNULATION_DETTE)
                : Set.of();
        return journal(
                jour, caisseId, utilisateurId, statut, type, utilisateurId != null, types, croissant, page, taille);
    }

    /** Journal de l'utilisateur connecté (Mon compte) : ses versements, alimentations et écarts de clôture. */
    @Transactional(readOnly = true)
    public PageResponse<Operation> journalDe(
            long utilisateurId,
            LocalDate jour,
            Long caisseId,
            JournalTresorerieJdbc.FiltreStatut statut,
            JournalTresorerieJdbc.FiltreType type,
            boolean croissant,
            int page,
            int taille) {
        return journal(
                jour,
                caisseId,
                utilisateurId,
                statut,
                type,
                true,
                Set.of(
                        TypeOperation.ALIMENTATION,
                        TypeOperation.VERSEMENT_ENVOYE,
                        TypeOperation.CLOTURE_SESSION,
                        TypeOperation.REMBOURSEMENT_DETTE,
                        TypeOperation.ANNULATION_DETTE),
                croissant,
                page,
                taille);
    }

    private PageResponse<Operation> journal(
            LocalDate jour,
            Long caisseId,
            Long utilisateurId,
            JournalTresorerieJdbc.FiltreStatut statut,
            JournalTresorerieJdbc.FiltreType type,
            boolean vueCompte,
            Set<TypeOperation> types,
            boolean croissant,
            int page,
            int taille) {
        ZoneId fuseau = fuseau();
        Filtre filtre = new Filtre(
                jour == null ? null : jour.atStartOfDay(fuseau).toInstant(),
                jour == null ? null : jour.plusDays(1).atStartOfDay(fuseau).toInstant(),
                caisseId,
                utilisateurId,
                statut,
                types,
                type,
                vueCompte);
        long total = operations.compter(filtre);
        return new PageResponse<>(operations.lister(filtre, croissant, page, taille), page, taille, total, (int)
                ((total + taille - 1) / taille));
    }

    // ---------------------------------------------------------------- Dettes des clients

    /**
     * Remboursement de la dette d'un client, dans la transaction du domaine clients : le compte de qui
     * encaisse est crédité (l'argent est à lui de le verser) ; l'admin l'encaisse directement dans la
     * caisse principale.
     *
     * @return la session ouverte du caissier, où l'argent entre dans le tiroir, ou null
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long encaisserDette(UtilisateurConnecte utilisateur, BigDecimal montant, String libelle) {
        if (utilisateur.estAdmin()) {
            Compte principale = comptes.principale();
            comptes.crediter(principale.id(), montant);
            journaliser(
                    TypeOperation.REMBOURSEMENT_DETTE,
                    principale.id(),
                    montant,
                    null,
                    null,
                    null,
                    null,
                    libelle,
                    utilisateur.id(),
                    null);
            return null;
        }
        Compte compte = comptes.caissierOuCree(utilisateur.id());
        SessionCaisse session =
                sessions.findOuverteDuCaissierForUpdate(utilisateur.id()).orElse(null);
        comptes.crediter(compte.id(), montant);
        journaliser(
                TypeOperation.REMBOURSEMENT_DETTE,
                compte.id(),
                montant,
                utilisateur.id(),
                session,
                null,
                null,
                libelle,
                utilisateur.id(),
                null);
        return session == null ? null : session.getId();
    }

    /**
     * Annulation par l'admin d'un remboursement : le compte qui l'avait reçu est débité. Le compte d'un
     * caissier peut devenir négatif (il a déjà versé l'argent) ; la caisse principale, jamais.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void annulerDette(
            long encaisseParId, boolean caissePrincipale, BigDecimal montant, String libelle, long adminId) {
        if (caissePrincipale) {
            Compte principale = comptes.principale();
            comptes.debiter(principale.id(), montant, BigDecimal.ZERO)
                    .orElseThrow(() -> soldeInsuffisant("de la caisse principale", principale.solde()));
            journaliser(
                    TypeOperation.ANNULATION_DETTE,
                    principale.id(),
                    montant.negate(),
                    null,
                    null,
                    null,
                    null,
                    libelle,
                    adminId,
                    null);
            return;
        }
        Compte compte = comptes.caissierOuCree(encaisseParId);
        comptes.crediter(compte.id(), montant.negate());
        journaliser(
                TypeOperation.ANNULATION_DETTE,
                compte.id(),
                montant.negate(),
                encaisseParId,
                null,
                null,
                null,
                libelle,
                adminId,
                null);
    }

    // ---------------------------------------------------------------- Mouvements

    /** Répartition d'un versement entre espèces et tickets TPE. */
    private record Repartition(BigDecimal especes, BigDecimal tpe) {
        BigDecimal total() {
            return especes.add(tpe);
        }
    }

    /**
     * Débite l'émetteur, crée le versement en transit et l'inscrit au journal ; {@code repartition} est absente
     * quand elle n'est pas saisie (versement de la caisse principale, comptage de l'admin).
     */
    private Versement envoyer(
            Compte emetteur,
            Compte recepteur,
            BigDecimal montant,
            Repartition repartition,
            String motif,
            SessionCaisse sessionEmetteur,
            boolean cloture,
            BigDecimal plancher,
            UUID cle,
            long auteurId,
            BigDecimal tiroirEspeces,
            BigDecimal tiroirTpe) {
        ReglesTresorerie.verifierSens(emetteur.type(), recepteur.type());
        boolean depuisPrincipale = emetteur.type() == TypeCompte.PRINCIPALE;
        long caissierId = depuisPrincipale ? recepteur.utilisateurId() : emetteur.utilisateurId();
        comptes.debiter(emetteur.id(), montant, plancher)
                .orElseThrow(() -> depuisPrincipale
                        ? soldeInsuffisant("de la caisse principale", emetteur.solde())
                        : soldeInsuffisant("de votre compte", soldeAffiche(caissierId)));
        Instant maintenant = Instant.now(clock);
        long id = versements.inserer(new NouveauVersement(
                emetteur.id(),
                recepteur.id(),
                montant,
                repartition == null ? null : repartition.especes(),
                repartition == null ? null : repartition.tpe(),
                motif,
                sessionEmetteur == null ? null : sessionEmetteur.getId(),
                tiroirEspeces,
                tiroirTpe,
                cloture,
                cle,
                auteurId,
                maintenant));
        journaliser(
                TypeOperation.VERSEMENT_ENVOYE,
                emetteur.id(),
                montant.negate(),
                caissierId,
                sessionEmetteur,
                id,
                cloture && sessionEmetteur != null ? sessionEmetteur.getEcart() : null,
                motif,
                auteurId,
                null);
        journal.enregistrer(
                auteurId,
                ActionJournal.VERSEMENT_ENVOYE,
                "Versement " + id + " de " + montant.toPlainString() + " DH "
                        + (depuisPrincipale
                                ? "de la caisse principale à l'utilisateur " + caissierId
                                : "de l'utilisateur " + caissierId + " à la caisse principale")
                        + (cloture ? " (clôture)" : ""));
        return versements.parId(id).orElseThrow();
    }

    /**
     * Crédite le récepteur. Un caissier dont la caisse est ouverte met {@code tiroir} dans son tiroir et garde
     * le reste sur lui (tout, s'il n'a rien indiqué) ; sans caisse ouverte, tout va sur son compte.
     */
    private Versement valider(Versement versement, long auteurId, boolean auto, BigDecimal tiroir) {
        Compte recepteur = comptes.parId(versement.recepteurId());
        SessionCaisse session = recepteur.type() == TypeCompte.CAISSIER
                ? sessions.findOuverteDuCaissierForUpdate(versement.caissierId())
                        .orElse(null)
                : null;
        if (!versements.traiter(
                versement.id(),
                StatutVersement.VALIDE,
                null,
                auto,
                session == null ? null : session.getId(),
                session == null ? null : (tiroir == null ? BigDecimal.ZERO : tiroir),
                auteurId,
                Instant.now(clock))) {
            throw new ConflitException("Ce versement a déjà été traité.");
        }
        comptes.crediter(recepteur.id(), versement.montant());
        journaliser(
                TypeOperation.VERSEMENT_RECU,
                recepteur.id(),
                versement.montant(),
                versement.caissierId(),
                session,
                versement.id(),
                null,
                auto ? "Auto-validé" : null,
                auteurId,
                null);
        journal.enregistrer(
                auteurId,
                ActionJournal.VERSEMENT_VALIDE,
                "Versement " + versement.id() + " de " + versement.montant().toPlainString() + " DH"
                        + (auto ? " auto-validé" : " reçu"));
        return versements.parId(versement.id()).orElseThrow();
    }

    /** Refus ou annulation : l'argent revient sur le compte de l'émetteur. */
    private Versement rendre(
            Versement versement, StatutVersement statut, String motif, long auteurId, TypeOperation type) {
        if (!versements.traiter(versement.id(), statut, motif, false, null, null, auteurId, Instant.now(clock))) {
            throw new ConflitException("Ce versement a déjà été traité.");
        }
        comptes.crediter(versement.emetteurId(), versement.montant());
        journaliser(
                type,
                versement.emetteurId(),
                versement.montant(),
                versement.caissierId(),
                null,
                versement.id(),
                null,
                motif,
                auteurId,
                null);
        journal.enregistrer(
                auteurId,
                statut == StatutVersement.REFUSE ? ActionJournal.VERSEMENT_REFUSE : ActionJournal.VERSEMENT_ANNULE,
                "Versement " + versement.id() + " de " + versement.montant().toPlainString() + " DH"
                        + (motif == null ? "" : " : " + motif));
        return versements.parId(versement.id()).orElseThrow();
    }

    /** Inscrit le mouvement au journal avec les soldes, juste après, de la caisse principale et du caissier. */
    private void journaliser(
            TypeOperation type,
            long compteId,
            BigDecimal montant,
            Long caissierId,
            SessionCaisse session,
            Long versementId,
            BigDecimal ecart,
            String motif,
            long auteurId,
            UUID cle) {
        operations.inserer(new NouvelleOperation(
                type,
                compteId,
                montant,
                caissierId,
                session == null ? null : session.getCaisseId(),
                session == null ? null : session.getId(),
                versementId,
                ecart,
                motif,
                comptes.principale().solde(),
                caissierId == null ? null : soldeAffiche(caissierId),
                auteurId,
                cle,
                Instant.now(clock)));
    }

    // ---------------------------------------------------------------- Lectures

    /**
     * Solde dû à la caisse principale : le solde stocké. Les ventes d'une session ouverte n'y entrent qu'à la
     * clôture, même si un versement arrive pendant la session.
     */
    private BigDecimal soldeAffiche(long utilisateurId) {
        return comptes.caissier(utilisateurId).map(Compte::solde).orElse(BigDecimal.ZERO);
    }

    /** Solde dû réparti entre espèces et tickets TPE. */
    private DuParMode du(long utilisateurId) {
        return DuParMode.depuis(
                soldeAffiche(utilisateurId),
                MouvementTpe.nonVerses(versements
                        .mouvementsTpe(List.of(utilisateurId), false)
                        .getOrDefault(utilisateurId, List.of())));
    }

    /**
     * Plafonds d'un versement à la caisse principale. Caisse ouverte, le dû est pris ventes de la session
     * comprises (comme si elle était clôturée maintenant) et partagé entre le tiroir et ce qui est hors tiroir.
     */
    private PlafondsVersement plafonds(long utilisateurId, SessionCaisse session) {
        if (session == null) return PlafondsVersement.horsSession(du(utilisateurId));
        ComptageTiroir tiroir = tiroir(session);
        DuParMode duAvecSession = DuParMode.depuis(
                soldeAffiche(utilisateurId).add(tiroir.ventes()),
                MouvementTpe.nonVerses(
                        versements.mouvementsTpe(List.of(utilisateurId), true).getOrDefault(utilisateurId, List.of())));
        return PlafondsVersement.enSession(
                duAvecSession, tiroir.attenduEspeces(session.getFondInitial()), tiroir.attenduTpe());
    }

    private ComptageTiroir tiroir(SessionCaisse session) {
        VentesSession ventes = encaissements.ventes(session.getId());
        MouvementsSession mouvements = mouvementsSession(session.getId());
        return new ComptageTiroir(
                ventes.especes(),
                ventes.carte(),
                mouvements.recu(),
                mouvements.recuTpe(),
                mouvements.verseEspeces(),
                mouvements.verseTpe(),
                mouvements.dettesEspeces());
    }

    private Optional<VersementDto> dejaEnvoye(UUID cle, long auteurId) {
        if (cle == null) return Optional.empty();
        return versements.parCle(cle).map(v -> {
            if (v.creePar() != auteurId) throw new ConflitException("Clé d'idempotence déjà utilisée.");
            return VersementDto.depuis(v);
        });
    }

    private void verifierRecepteur(Versement versement, UtilisateurConnecte utilisateur) {
        boolean recepteur =
                versement.versLaPrincipale() ? utilisateur.estAdmin() : versement.caissierId() == utilisateur.id();
        if (!recepteur) {
            throw new OperationInterditeException("Seul le destinataire peut valider ou refuser ce versement.");
        }
    }

    private Versement chargerPourMaj(long versementId) {
        return versements
                .parIdPourMaj(versementId)
                .orElseThrow(() -> new RessourceIntrouvableException("Versement introuvable."));
    }

    private Utilisateur chargerUtilisateur(long utilisateurId) {
        return utilisateurs
                .findById(utilisateurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));
    }

    private boolean estAdmin(long utilisateurId) {
        return utilisateurs
                .findById(utilisateurId)
                .map(u -> u.getRole() == Role.ADMIN)
                .orElse(false);
    }

    private static RegleMetierException soldeInsuffisant(String compte, BigDecimal solde) {
        return new RegleMetierException(
                "Solde " + compte + " insuffisant (" + solde.toPlainString() + " DH disponibles).");
    }

    private Instant debutDuJour() {
        ZoneId fuseau = fuseau();
        return LocalDate.now(clock.withZone(fuseau)).atStartOfDay(fuseau).toInstant();
    }

    private ZoneId fuseau() {
        return ZoneId.of(parametres.lire().fuseauHoraire());
    }

    private static List<VersementDto> dtos(List<Versement> liste) {
        return liste.stream().map(VersementDto::depuis).toList();
    }
}
