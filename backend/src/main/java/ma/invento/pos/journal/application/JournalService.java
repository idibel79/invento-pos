package ma.invento.pos.journal.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.Instant;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.journal.domain.JournalAction;
import ma.invento.pos.journal.infrastructure.JournalActionRepository;
import ma.invento.pos.shared.api.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JournalService {

    private static final int DETAIL_MAX = 1000;

    private final JournalActionRepository repository;
    private final Clock clock;

    public JournalService(JournalActionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Inscrit une action dans la transaction en cours. Le détail ne doit contenir ni mot de passe,
     * ni PIN, ni code.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enregistrer(Long utilisateurId, ActionJournal action, String detail) {
        repository.save(new JournalAction(utilisateurId, action, tronquer(detail), Instant.now(clock)));
    }

    /**
     * Inscrit une action dans sa propre transaction, pour qu'elle reste au journal même si la
     * transaction appelante est annulée (ex. tentative de connexion échouée).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrerSepare(Long utilisateurId, ActionJournal action, String detail) {
        repository.save(new JournalAction(utilisateurId, action, tronquer(detail), Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public PageResponse<JournalDto> lister(Pageable pageable) {
        return PageResponse.of(repository.findAllByOrderByCreeLeDescIdDesc(pageable), JournalDto::depuis);
    }

    private static String tronquer(String detail) {
        if (detail == null || detail.length() <= DETAIL_MAX) return detail;
        return detail.substring(0, DETAIL_MAX);
    }

    public record JournalDto(
            Long id,
            @Schema(nullable = true) Long utilisateurId,
            ActionJournal action,
            @Schema(nullable = true) String detail,
            Instant creeLe) {
        static JournalDto depuis(JournalAction a) {
            return new JournalDto(a.getId(), a.getUtilisateurId(), a.getAction(), a.getDetail(), a.getCreeLe());
        }
    }
}
