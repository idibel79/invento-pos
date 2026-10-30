package ma.invento.pos.journal.api;

import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.application.JournalService.JournalDto;
import ma.invento.pos.shared.api.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/journal")
@PreAuthorize("hasRole('ADMIN')")
class JournalController {

    private final JournalService journalService;

    JournalController(JournalService journalService) {
        this.journalService = journalService;
    }

    @GetMapping
    PageResponse<JournalDto> lister(@PageableDefault(size = 50) Pageable pageable) {
        return journalService.lister(pageable);
    }
}
