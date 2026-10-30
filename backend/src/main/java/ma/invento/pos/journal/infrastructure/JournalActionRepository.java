package ma.invento.pos.journal.infrastructure;

import java.util.List;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.journal.domain.JournalAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalActionRepository extends JpaRepository<JournalAction, Long> {

    Page<JournalAction> findAllByOrderByCreeLeDescIdDesc(Pageable pageable);

    List<JournalAction> findByAction(ActionJournal action);
}
