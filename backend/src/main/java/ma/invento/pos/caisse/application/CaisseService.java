package ma.invento.pos.caisse.application;

import java.util.List;
import java.util.Set;
import ma.invento.pos.caisse.domain.Caisse;
import ma.invento.pos.caisse.infrastructure.CaisseRepository;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.shared.domain.ConflitException;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaisseService {

    private final CaisseRepository caisses;
    private final JournalService journal;

    public CaisseService(CaisseRepository caisses, JournalService journal) {
        this.caisses = caisses;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    public List<CaisseDto> lister() {
        return caisses.findAllByOrderByNomAsc().stream().map(CaisseDto::depuis).toList();
    }

    @Transactional
    public CaisseDto creer(String nom, long adminId) {
        String propre = nom.trim();
        if (caisses.existsByNomIgnoreCase(propre)) {
            throw new ConflitException("La caisse " + propre + " existe déjà.");
        }
        Caisse caisse = caisses.save(new Caisse(propre));
        journal.enregistrer(adminId, ActionJournal.CAISSE_CREEE, propre);
        return CaisseDto.depuis(caisse);
    }

    @Transactional
    public CaisseDto modifier(long id, String nom, boolean active, long adminId) {
        String propre = nom.trim();
        Caisse caisse =
                caisses.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Caisse introuvable."));
        if (caisses.existsByNomIgnoreCaseAndIdNot(propre, id)) {
            throw new ConflitException("La caisse " + propre + " existe déjà.");
        }
        caisse.modifier(propre, active);
        journal.enregistrer(adminId, ActionJournal.CAISSE_MODIFIEE, propre + (active ? "" : " (désactivée)"));
        return CaisseDto.depuis(caisse);
    }

    /** Vérifie que toutes les caisses citées existent (affectation d'un utilisateur). */
    @Transactional(readOnly = true)
    public void verifierExistence(Set<Long> caisseIds) {
        if (!caisseIds.isEmpty() && caisses.countByIdIn(caisseIds) != caisseIds.size()) {
            throw new RessourceIntrouvableException("Caisse introuvable.");
        }
    }

    public record CaisseDto(Long id, String nom, boolean active) {
        static CaisseDto depuis(Caisse caisse) {
            return new CaisseDto(caisse.getId(), caisse.getNom(), caisse.isActive());
        }
    }
}
