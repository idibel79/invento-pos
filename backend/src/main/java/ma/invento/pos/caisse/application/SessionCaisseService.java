package ma.invento.pos.caisse.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.invento.pos.caisse.application.ComptesCaissiers.MouvementsSession;
import ma.invento.pos.caisse.application.EncaissementsSession.VentesSession;
import ma.invento.pos.caisse.domain.Caisse;
import ma.invento.pos.caisse.domain.ComptageTiroir;
import ma.invento.pos.caisse.domain.SessionCaisse;
import ma.invento.pos.caisse.infrastructure.CaisseRepository;
import ma.invento.pos.caisse.infrastructure.SessionCaisseRepository;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.shared.domain.ConflitException;
import ma.invento.pos.shared.domain.OperationInterditeException;
import ma.invento.pos.shared.domain.RegleMetierException;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Ouverture et clôture des sessions de caisse (le rapport Z détaillé viendra à l'étape 3). */
@Service
public class SessionCaisseService {

    /** Code d'erreur quand le vendeur n'a plus de session ouverte : l'écran revient à l'ouverture. */
    public static final String SESSION_FERMEE = "SESSION_FERMEE";

    private final SessionCaisseRepository sessions;
    private final CaisseRepository caisses;
    private final UtilisateurRepository utilisateurs;
    private final EncaissementsSession encaissements;
    private final ComptesCaissiers comptes;
    private final ApplicationEventPublisher evenements;
    private final JournalService journal;
    private final Clock clock;

    public SessionCaisseService(
            SessionCaisseRepository sessions,
            CaisseRepository caisses,
            UtilisateurRepository utilisateurs,
            EncaissementsSession encaissements,
            ComptesCaissiers comptes,
            ApplicationEventPublisher evenements,
            JournalService journal,
            Clock clock) {
        this.sessions = sessions;
        this.caisses = caisses;
        this.utilisateurs = utilisateurs;
        this.encaissements = encaissements;
        this.comptes = comptes;
        this.evenements = evenements;
        this.journal = journal;
        this.clock = clock;
    }

    @Transactional
    public SessionCaisseDto ouvrir(long caisseId, BigDecimal fondInitial, UtilisateurConnecte utilisateur) {
        Caisse caisse = caisses.findById(caisseId)
                .filter(Caisse::isActive)
                .orElseThrow(() -> new RessourceIntrouvableException("Caisse introuvable ou désactivée."));
        if (!utilisateur.estAdmin() && !caisseAutorisee(utilisateur.id(), caisseId)) {
            throw new OperationInterditeException("Vous n'êtes pas autorisé sur la caisse " + caisse.getNom() + ".");
        }
        if (sessions.findByCaissierIdAndFermetureIsNull(utilisateur.id()).isPresent()) {
            throw new ConflitException("Vous avez déjà une session de caisse ouverte.");
        }
        if (sessions.existsByCaisseIdAndFermetureIsNull(caisseId)) {
            throw new ConflitException("La caisse " + caisse.getNom() + " est déjà ouverte par un autre utilisateur.");
        }
        // Le fond passe de la poche du caissier au tiroir : il ne change pas le solde de son compte.
        BigDecimal solde = comptes.solde(utilisateur.id());
        if (fondInitial.compareTo(solde) > 0) {
            throw new RegleMetierException("Le fond de caisse dépasse le solde de votre compte ("
                    + solde.toPlainString() + " DH disponibles).");
        }
        SessionCaisse session =
                sessions.save(new SessionCaisse(caisseId, utilisateur.id(), fondInitial, Instant.now(clock)));
        journal.enregistrer(
                utilisateur.id(),
                ActionJournal.SESSION_OUVERTE,
                caisse.getNom() + ", fond " + fondInitial.toPlainString() + " DH");
        return SessionCaisseDto.depuis(session, caisse.getNom(), BigDecimal.ZERO, null);
    }

    @Transactional(readOnly = true)
    public Optional<SessionCaisseDto> courante(long utilisateurId) {
        return sessions.findByCaissierIdAndFermetureIsNull(utilisateurId)
                .map(s -> SessionCaisseDto.depuis(s, nomCaisse(s.getCaisseId()), null, null));
    }

    /**
     * Session ouverte avec le contenu attendu du tiroir et le solde du compte, pour la barre de caisse
     * et la popup de clôture.
     */
    @Transactional(readOnly = true)
    public Optional<SessionCaisseDto> couranteAvecEspeces(long utilisateurId) {
        return sessions.findByCaissierIdAndFermetureIsNull(utilisateurId).map(s -> {
            ComptageTiroir tiroir = tiroir(s.getId());
            return SessionCaisseDto.depuis(
                    s,
                    nomCaisse(s.getCaisseId()),
                    tiroir.ventesEspeces(),
                    TiroirDto.depuis(s, tiroir, comptes.solde(utilisateurId)));
        });
    }

    /**
     * Espèces présentes dans le tiroir, de quoi rendre la monnaie : fond + ventes en espèces (nettes du
     * rendu) + versements reçus − versements faits pendant la session. Les tickets TPE n'en font pas
     * partie. Appelé sous le verrou de la session ({@link #sessionOuverte}).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal especesDisponibles(long sessionId) {
        SessionCaisse session = sessions.findById(sessionId).orElseThrow();
        ComptageTiroir tiroir = tiroir(sessionId);
        return session.getFondInitial()
                .add(tiroir.ventesEspeces())
                .add(tiroir.recu())
                .subtract(tiroir.verse());
    }

    private ComptageTiroir tiroir(long sessionId) {
        VentesSession ventes = encaissements.ventes(sessionId);
        MouvementsSession mouvements = comptes.mouvementsSession(sessionId);
        return new ComptageTiroir(ventes.especes(), ventes.carte(), mouvements.recu(), mouvements.verse());
    }

    /** Toutes les caisses avec, pour celles qui sont ouvertes, le fond et les espèces (écran admin). */
    @Transactional(readOnly = true)
    public List<SuiviCaisseDto> suivi() {
        Map<Long, SessionCaisse> ouvertes = sessions.findByFermetureIsNull().stream()
                .collect(Collectors.toMap(SessionCaisse::getCaisseId, Function.identity()));
        Map<Long, BigDecimal> especes = encaissements.especesEncaisseesParSession(
                ouvertes.values().stream().map(SessionCaisse::getId).toList());
        return caisses.findAllBySupprimeeFalseOrderByNomAsc().stream()
                .map(c -> new SuiviCaisseDto(
                        c.getId(),
                        c.getNom(),
                        c.isActive(),
                        Optional.ofNullable(ouvertes.get(c.getId()))
                                .map(s -> SessionEnCours.depuis(s, especes.getOrDefault(s.getId(), BigDecimal.ZERO)))
                                .orElse(null)))
                .toList();
    }

    /** Session ouverte de l'utilisateur, verrouillée jusqu'à la fin de la transaction appelante. */
    @Transactional(propagation = Propagation.MANDATORY)
    public SessionOuverte sessionOuverte(long utilisateurId) {
        SessionCaisse session =
                sessions.findOuverteDuCaissierForUpdate(utilisateurId).orElseThrow(() -> sessionFermee(utilisateurId));
        // Par défense : la désactivation clôture déjà la session dans la même transaction.
        Caisse caisse = caisses.findById(session.getCaisseId()).orElseThrow();
        if (!caisse.isActive()) {
            throw new RegleMetierException(
                    "La caisse " + caisse.getNom() + " a été désactivée : la session est clôturée.", SESSION_FERMEE);
        }
        return new SessionOuverte(session.getId(), session.getCaisseId());
    }

    /**
     * Caisse désactivée par l'admin : sa session ouverte est clôturée sans comptage et ses ventes en
     * attente annulées, dans la transaction de la désactivation.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cloturerDOffice(long caisseId, long adminId) {
        sessions.findOuverteDeLaCaisseForUpdate(caisseId).ifPresent(session -> {
            evenements.publishEvent(new SessionCaisseCloturee(session.getId()));
            session.cloturerDOffice(Instant.now(clock));
            comptes.cloturerDOffice(
                    session, encaissements.ventes(session.getId()).total(), adminId);
            journal.enregistrer(
                    adminId,
                    ActionJournal.SESSION_CLOTUREE_D_OFFICE,
                    nomCaisse(caisseId) + ", caisse désactivée : session de l'utilisateur " + session.getCaissierId()
                            + " clôturée, comptage du tiroir en attente");
        });
    }

    /**
     * Clôture par le caissier de la session (ou l'admin) : ventes en attente annulées, tiroir compté
     * (espèces et tickets TPE), écart calculé, puis le tiroir est vidé : {@code montantVerse} part vers
     * la caisse principale, le reste demeure sur le compte du caissier.
     */
    @Transactional
    public SessionCaisseDto cloturer(
            long sessionId,
            BigDecimal especes,
            BigDecimal tpe,
            BigDecimal montantVerse,
            UtilisateurConnecte utilisateur) {
        SessionCaisse session = sessions.findForUpdate(sessionId)
                .orElseThrow(() -> new RessourceIntrouvableException("Session de caisse introuvable."));
        if (!utilisateur.estAdmin() && session.getCaissierId() != utilisateur.id()) {
            throw new OperationInterditeException("Seul le caissier de la session peut la clôturer.");
        }
        if (!session.estOuverte()) {
            throw new RegleMetierException("Cette session de caisse est déjà clôturée.", SESSION_FERMEE);
        }
        verifierVerse(especes.add(tpe), montantVerse);
        evenements.publishEvent(new SessionCaisseCloturee(sessionId));
        ComptageTiroir tiroir = tiroir(sessionId);
        session.cloturer(especes, tpe, tiroir, Instant.now(clock));
        comptes.cloturer(session, tiroir.ventes(), montantVerse, utilisateur.id());
        String caisse = nomCaisse(session.getCaisseId());
        journal.enregistrer(
                utilisateur.id(),
                ActionJournal.SESSION_CLOTUREE,
                caisse + ", " + comptage(session) + ", versé " + montantVerse.toPlainString()
                        + " DH à la caisse principale");
        return SessionCaisseDto.depuis(
                session, caisse, tiroir.ventesEspeces(), TiroirDto.depuis(session, tiroir, null));
    }

    /**
     * Comptage par l'admin du tiroir d'une session clôturée d'office : l'écart est calculé comme à une
     * clôture normale, {@code montantVerse} entre dans la caisse principale (l'admin a le tiroir).
     */
    @Transactional
    public SessionCaisseDto terminerComptage(
            long sessionId, BigDecimal especes, BigDecimal tpe, BigDecimal montantVerse, long adminId) {
        SessionCaisse session = sessions.findForUpdate(sessionId)
                .orElseThrow(() -> new RessourceIntrouvableException("Session de caisse introuvable."));
        verifierVerse(especes.add(tpe), montantVerse);
        ComptageTiroir tiroir = tiroir(sessionId);
        session.terminerComptage(especes, tpe, tiroir);
        comptes.terminerComptage(session, montantVerse, adminId);
        String caisse = nomCaisse(session.getCaisseId());
        journal.enregistrer(
                adminId,
                ActionJournal.SESSION_COMPTEE,
                caisse + ", session de l'utilisateur " + session.getCaissierId() + " : " + comptage(session)
                        + ", versé " + montantVerse.toPlainString() + " DH à la caisse principale");
        return SessionCaisseDto.depuis(
                session, caisse, tiroir.ventesEspeces(), TiroirDto.depuis(session, tiroir, null));
    }

    /** Sessions clôturées d'office dont le tiroir attend le comptage de l'admin. */
    @Transactional(readOnly = true)
    public List<SessionCaisseDto> comptagesEnAttente() {
        return sessions.findByComptageEnAttenteTrueOrderByFermetureAsc().stream()
                .map(s -> {
                    ComptageTiroir tiroir = tiroir(s.getId());
                    return SessionCaisseDto.depuis(
                            s, nomCaisse(s.getCaisseId()), tiroir.ventesEspeces(), TiroirDto.depuis(s, tiroir, null));
                })
                .toList();
    }

    /** Le tiroir est vidé : on verse entre 0 et tout le compté. */
    private static void verifierVerse(BigDecimal montantCompte, BigDecimal montantVerse) {
        if (montantVerse.signum() < 0 || montantVerse.compareTo(montantCompte) > 0) {
            throw new RegleMetierException("Le montant versé doit être compris entre 0 et le montant compté.");
        }
    }

    /** Résumé du comptage pour le journal d'audit : compté et écart, au total puis par mode. */
    private static String comptage(SessionCaisse s) {
        return "compté " + s.getMontantCompte().toPlainString() + " DH (espèces "
                + s.getMontantCompteEspeces().toPlainString() + ", TPE "
                + s.getMontantCompteTpe().toPlainString()
                + "), écart " + s.getEcart().toPlainString() + " DH (espèces "
                + s.getEcartEspeces().toPlainString()
                + ", TPE " + s.getEcartTpe().toPlainString() + ")";
    }

    /** Plus de session ouverte : le message précise si la dernière a été clôturée d'office. */
    private RegleMetierException sessionFermee(long utilisateurId) {
        return sessions.findFirstByCaissierIdOrderByOuvertureDesc(utilisateurId)
                .filter(SessionCaisse::isClotureForcee)
                .map(s -> new RegleMetierException(
                        "La caisse " + nomCaisse(s.getCaisseId()) + " a été désactivée : la session est clôturée.",
                        SESSION_FERMEE))
                .orElseGet(() -> new RegleMetierException("Ouvrez une session de caisse pour vendre.", SESSION_FERMEE));
    }

    private boolean caisseAutorisee(long utilisateurId, long caisseId) {
        return utilisateurs
                .findById(utilisateurId)
                .map(u -> u.getCaisseIds().contains(caisseId))
                .orElse(false);
    }

    private String nomCaisse(long caisseId) {
        return caisses.findById(caisseId).map(Caisse::getNom).orElse("?");
    }

    public record SessionOuverte(long id, long caisseId) {}

    public record SuiviCaisseDto(
            Long id,
            String nom,
            boolean active,
            @Schema(nullable = true) SessionEnCours session) {}

    /** Espèces attendues dans le tiroir = fond de caisse + espèces encaissées. */
    public record SessionEnCours(
            Long caissierId,
            Instant ouverture,
            BigDecimal fondInitial,
            BigDecimal especesEncaissees,
            BigDecimal especesAttendues) {

        static SessionEnCours depuis(SessionCaisse s, BigDecimal especesEncaissees) {
            return new SessionEnCours(
                    s.getCaissierId(),
                    s.getOuverture(),
                    s.getFondInitial(),
                    especesEncaissees,
                    s.getFondInitial().add(especesEncaissees));
        }
    }

    public record SessionCaisseDto(
            Long id,
            Long caisseId,
            String caisseNom,
            Long caissierId,
            Instant ouverture,
            @Schema(nullable = true) Instant fermeture,
            BigDecimal fondInitial,
            @Schema(nullable = true) BigDecimal montantCompte,
            @Schema(nullable = true) BigDecimal montantCompteEspeces,
            @Schema(nullable = true) BigDecimal montantCompteTpe,
            @Schema(nullable = true) BigDecimal especesEncaissees,
            @Schema(nullable = true) BigDecimal ecart,
            @Schema(nullable = true) BigDecimal ecartEspeces,
            @Schema(nullable = true) BigDecimal ecartTpe,
            boolean clotureForcee,
            boolean comptageEnAttente,
            @Schema(nullable = true) TiroirDto tiroir) {

        static SessionCaisseDto depuis(
                SessionCaisse s, String caisseNom, BigDecimal especesEncaissees, TiroirDto tiroir) {
            return new SessionCaisseDto(
                    s.getId(),
                    s.getCaisseId(),
                    caisseNom,
                    s.getCaissierId(),
                    s.getOuverture(),
                    s.getFermeture(),
                    s.getFondInitial(),
                    s.getMontantCompte(),
                    s.getMontantCompteEspeces(),
                    s.getMontantCompteTpe(),
                    especesEncaissees,
                    s.getEcart(),
                    s.getEcartEspeces(),
                    s.getEcartTpe(),
                    s.isClotureForcee(),
                    s.isComptageEnAttente(),
                    tiroir);
        }
    }

    /**
     * Contenu attendu du tiroir (espèces et tickets TPE) : fond + ventes espèces + ventes carte + reçu de
     * la caisse principale − versé pendant la session. {@code soldeCompte} : ce que le caissier doit à la
     * caisse principale, ventes de la session comprises (absent après la clôture).
     */
    public record TiroirDto(
            BigDecimal ventesEspeces,
            BigDecimal ventesCarte,
            BigDecimal recu,
            BigDecimal verse,
            BigDecimal attendu,
            BigDecimal attenduEspeces,
            BigDecimal attenduTpe,
            @Schema(nullable = true) BigDecimal soldeCompte) {

        static TiroirDto depuis(SessionCaisse s, ComptageTiroir tiroir, BigDecimal soldeCompte) {
            return new TiroirDto(
                    tiroir.ventesEspeces(),
                    tiroir.ventesCarte(),
                    tiroir.recu(),
                    tiroir.verse(),
                    tiroir.attendu(s.getFondInitial()),
                    tiroir.attenduEspeces(s.getFondInitial()),
                    tiroir.attenduTpe(),
                    soldeCompte);
        }
    }
}
