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
        return new MouvementsSession(recuEtVerse[0], recuEtVerse[1]);
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
                    compte, comptes.principale(), montantVerse, null, session, true, BigDecimal.ZERO, null, adminId);
            valider(versement, adminId, false);
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
        return new MonCompteDto(soldeAffiche(utilisateurId), session.isPresent(), attendu, aValider, historique);
    }

    /** Versement du caissier vers la caisse principale, à tout moment, si son solde le permet. */
    @Transactional
    public VersementDto verserALaPrincipale(UtilisateurConnecte utilisateur, BigDecimal montant, UUID cle) {
        ReglesTresorerie.verifierMontant(montant);
        Optional<VersementDto> deja = dejaEnvoye(cle, utilisateur.id());
        if (deja.isPresent()) return deja.get();
        Compte compte = comptes.caissierOuCree(utilisateur.id());
        Optional<SessionCaisse> session = sessions.findOuverteDuCaissierForUpdate(utilisateur.id());
        Versement versement = envoyer(
                compte,
                comptes.principale(),
                montant,
                null,
                session.orElse(null),
                false,
                plancher(session),
                cle,
                utilisateur.id());
        // L'admin qui est aussi caissier valide lui-même ses versements (auto-validation au journal).
        if (utilisateur.estAdmin()) versement = valider(versement, utilisateur.id(), true);
        return VersementDto.depuis(versement);
    }

    /** Confirmation par le récepteur qu'il a reçu l'argent : il est crédité. */
    @Transactional
    public VersementDto valider(long versementId, UtilisateurConnecte utilisateur) {
        Versement versement = chargerPourMaj(versementId);
        verifierRecepteur(versement, utilisateur);
        versement.statut().verifierEnTransit();
        return VersementDto.depuis(valider(versement, utilisateur.id(), false));
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
                propre,
                null,
                false,
                BigDecimal.ZERO,
                cle,
                adminId);
        if (utilisateurId == adminId) versement = valider(versement, adminId, true);
        return VersementDto.depuis(versement);
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
        comptes.debiter(compte.id(), montant, plancher(session))
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
        Map<Long, EnTransit> enTransit = versements.enTransitParCaissier();
        Map<Long, SessionCaisse> ouvertes = sessions.findByFermetureIsNull().stream()
                .collect(Collectors.toMap(SessionCaisse::getCaissierId, Function.identity()));
        Map<Long, VentesSession> ventes = encaissements.ventesParSession(
                ouvertes.values().stream().map(SessionCaisse::getId).toList());
        Map<Long, String> nomsCaisses =
                caisses
                        .findAllById(ouvertes.values().stream()
                                .map(SessionCaisse::getCaisseId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Caisse::getId, Caisse::getNom));
        List<CompteCaissierDto> caissiers = utilisateurs.findAllByOrderByNomAscPrenomAsc().stream()
                .map(u -> {
                    SessionCaisse session = ouvertes.get(u.getId());
                    BigDecimal ventesSession = session == null
                            ? BigDecimal.ZERO
                            : ventes.getOrDefault(session.getId(), VentesSession.AUCUNE)
                                    .total();
                    EnTransit transit = enTransit.getOrDefault(u.getId(), EnTransit.AUCUN);
                    return new CompteCaissierDto(
                            u.getId(),
                            (u.getPrenom() + " " + u.getNom()).trim(),
                            u.getRole(),
                            u.isActif(),
                            soldes.getOrDefault(u.getId(), BigDecimal.ZERO).add(ventesSession),
                            session == null ? null : nomsCaisses.get(session.getCaisseId()),
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
            boolean croissant,
            int page,
            int taille) {
        // Sans utilisateur : les seules opérations de la caisse principale. Avec : tout le compte de cet
        // utilisateur, clôtures (ventes) et ajustements compris.
        Set<TypeOperation> types = utilisateurId == null
                ? Set.of(TypeOperation.ALIMENTATION, TypeOperation.RETRAIT, TypeOperation.VERSEMENT_ENVOYE)
                : Set.of();
        return journal(jour, caisseId, utilisateurId, statut, types, croissant, page, taille);
    }

    /** Journal de l'utilisateur connecté (Mon compte) : ses versements, alimentations et écarts de clôture. */
    @Transactional(readOnly = true)
    public PageResponse<Operation> journalDe(
            long utilisateurId,
            LocalDate jour,
            JournalTresorerieJdbc.FiltreStatut statut,
            boolean croissant,
            int page,
            int taille) {
        return journal(
                jour,
                null,
                utilisateurId,
                statut,
                Set.of(TypeOperation.ALIMENTATION, TypeOperation.VERSEMENT_ENVOYE, TypeOperation.CLOTURE_SESSION),
                croissant,
                page,
                taille);
    }

    private PageResponse<Operation> journal(
            LocalDate jour,
            Long caisseId,
            Long utilisateurId,
            JournalTresorerieJdbc.FiltreStatut statut,
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
                types);
        long total = operations.compter(filtre);
        return new PageResponse<>(operations.lister(filtre, croissant, page, taille), page, taille, total, (int)
                ((total + taille - 1) / taille));
    }

    /** Un utilisateur ne peut être désactivé que si son compte est soldé et sans versement en transit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void verifierDesactivable(long utilisateurId) {
        Optional<Compte> compte = comptes.caissier(utilisateurId);
        if (compte.isEmpty()) return;
        if (soldeAffiche(utilisateurId).signum() != 0) {
            throw new RegleMetierException(
                    "Cet utilisateur doit encore " + soldeAffiche(utilisateurId).toPlainString()
                            + " DH à la caisse principale : soldez son compte avant de le désactiver.");
        }
        if (versements.existeEnTransit(compte.get().id())) {
            throw new RegleMetierException(
                    "Cet utilisateur a un versement en transit : validez-le, refusez-le ou annulez-le avant de le désactiver.");
        }
    }

    // ---------------------------------------------------------------- Mouvements

    /** Débite l'émetteur, crée le versement en transit et l'inscrit au journal. */
    private Versement envoyer(
            Compte emetteur,
            Compte recepteur,
            BigDecimal montant,
            String motif,
            SessionCaisse sessionEmetteur,
            boolean cloture,
            BigDecimal plancher,
            UUID cle,
            long auteurId) {
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
                motif,
                sessionEmetteur == null ? null : sessionEmetteur.getId(),
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

    /** Crédite le récepteur ; s'il a une session ouverte, l'argent entre dans son tiroir. */
    private Versement valider(Versement versement, long auteurId, boolean auto) {
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
        if (!versements.traiter(versement.id(), statut, motif, false, null, auteurId, Instant.now(clock))) {
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

    /** Solde dû à la caisse principale : solde stocké + ventes de la session ouverte. */
    private BigDecimal soldeAffiche(long utilisateurId) {
        BigDecimal stocke = comptes.caissier(utilisateurId).map(Compte::solde).orElse(BigDecimal.ZERO);
        return stocke.add(sessions.findByCaissierIdAndFermetureIsNull(utilisateurId)
                .map(s -> encaissements.ventes(s.getId()).total())
                .orElse(BigDecimal.ZERO));
    }

    /**
     * Plancher du solde stocké d'un caissier : ses ventes de la session ouverte n'y sont pas encore
     * reportées, il peut donc descendre jusqu'à leur opposé (solde affiché ≥ 0).
     */
    private BigDecimal plancher(Optional<SessionCaisse> sessionVerrouillee) {
        return sessionVerrouillee
                .map(s -> encaissements.ventes(s.getId()).total().negate())
                .orElse(BigDecimal.ZERO);
    }

    private ComptageTiroir tiroir(SessionCaisse session) {
        VentesSession ventes = encaissements.ventes(session.getId());
        MouvementsSession mouvements = mouvementsSession(session.getId());
        return new ComptageTiroir(ventes.especes(), ventes.carte(), mouvements.recu(), mouvements.verse());
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
