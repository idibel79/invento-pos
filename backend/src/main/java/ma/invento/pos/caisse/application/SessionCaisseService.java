package ma.invento.pos.caisse.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import ma.invento.pos.caisse.domain.Caisse;
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

    private final SessionCaisseRepository sessions;
    private final CaisseRepository caisses;
    private final UtilisateurRepository utilisateurs;
    private final EncaissementsSession encaissements;
    private final ApplicationEventPublisher evenements;
    private final JournalService journal;
    private final Clock clock;

    public SessionCaisseService(
            SessionCaisseRepository sessions,
            CaisseRepository caisses,
            UtilisateurRepository utilisateurs,
            EncaissementsSession encaissements,
            ApplicationEventPublisher evenements,
            JournalService journal,
            Clock clock) {
        this.sessions = sessions;
        this.caisses = caisses;
        this.utilisateurs = utilisateurs;
        this.encaissements = encaissements;
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
        SessionCaisse session =
                sessions.save(new SessionCaisse(caisseId, utilisateur.id(), fondInitial, Instant.now(clock)));
        journal.enregistrer(
                utilisateur.id(),
                ActionJournal.SESSION_OUVERTE,
                caisse.getNom() + ", fond " + fondInitial.toPlainString() + " DH");
        return SessionCaisseDto.depuis(session, caisse.getNom(), null);
    }

    @Transactional(readOnly = true)
    public Optional<SessionCaisseDto> courante(long utilisateurId) {
        return sessions.findByCaissierIdAndFermetureIsNull(utilisateurId)
                .map(s -> SessionCaisseDto.depuis(s, nomCaisse(s.getCaisseId()), null));
    }

    /** Session ouverte de l'utilisateur, verrouillée jusqu'à la fin de la transaction appelante. */
    @Transactional(propagation = Propagation.MANDATORY)
    public SessionOuverte sessionOuverte(long utilisateurId) {
        SessionCaisse session = sessions.findByCaissierIdAndFermetureIsNull(utilisateurId)
                .flatMap(s -> sessions.findForUpdate(s.getId()))
                .filter(SessionCaisse::estOuverte)
                .orElseThrow(() -> new RegleMetierException("Ouvrez une session de caisse pour vendre."));
        return new SessionOuverte(session.getId(), session.getCaisseId());
    }

    /** Clôture par le caissier de la session (ou l'admin) : ventes en attente annulées, écart calculé. */
    @Transactional
    public SessionCaisseDto cloturer(long sessionId, BigDecimal montantCompte, UtilisateurConnecte utilisateur) {
        SessionCaisse session = sessions.findForUpdate(sessionId)
                .orElseThrow(() -> new RessourceIntrouvableException("Session de caisse introuvable."));
        if (!utilisateur.estAdmin() && session.getCaissierId() != utilisateur.id()) {
            throw new OperationInterditeException("Seul le caissier de la session peut la clôturer.");
        }
        if (!session.estOuverte()) {
            throw new RegleMetierException("Cette session de caisse est déjà clôturée.");
        }
        evenements.publishEvent(new SessionCaisseCloturee(sessionId));
        BigDecimal especes = encaissements.especesEncaissees(sessionId);
        session.cloturer(montantCompte, especes, Instant.now(clock));
        String caisse = nomCaisse(session.getCaisseId());
        journal.enregistrer(
                utilisateur.id(),
                ActionJournal.SESSION_CLOTUREE,
                caisse + ", compté " + montantCompte.toPlainString() + " DH, écart "
                        + session.getEcart().toPlainString() + " DH");
        return SessionCaisseDto.depuis(session, caisse, especes);
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

    public record SessionCaisseDto(
            Long id,
            Long caisseId,
            String caisseNom,
            Long caissierId,
            Instant ouverture,
            @Schema(nullable = true) Instant fermeture,
            BigDecimal fondInitial,
            @Schema(nullable = true) BigDecimal montantCompte,
            @Schema(nullable = true) BigDecimal especesEncaissees,
            @Schema(nullable = true) BigDecimal ecart) {

        static SessionCaisseDto depuis(SessionCaisse s, String caisseNom, BigDecimal especesEncaissees) {
            return new SessionCaisseDto(
                    s.getId(),
                    s.getCaisseId(),
                    caisseNom,
                    s.getCaissierId(),
                    s.getOuverture(),
                    s.getFermeture(),
                    s.getFondInitial(),
                    s.getMontantCompte(),
                    especesEncaissees,
                    s.getEcart());
        }
    }
}
