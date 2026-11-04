package ma.invento.pos.caisse.application;

import java.util.List;
import java.util.Set;
import ma.invento.pos.caisse.domain.Caisse;
import ma.invento.pos.caisse.infrastructure.CaisseRepository;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.shared.domain.ConflitException;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaisseService {

    private final CaisseRepository caisses;
    private final SessionCaisseService sessions;
    private final UtilisateurRepository utilisateurs;
    private final JournalService journal;

    public CaisseService(
            CaisseRepository caisses,
            SessionCaisseService sessions,
            UtilisateurRepository utilisateurs,
            JournalService journal) {
        this.caisses = caisses;
        this.sessions = sessions;
        this.utilisateurs = utilisateurs;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    public List<CaisseDto> lister() {
        return caisses.findAllBySupprimeeFalseOrderByNomAsc().stream()
                .map(CaisseDto::depuis)
                .toList();
    }

    @Transactional
    public CaisseDto creer(String nom, long adminId) {
        String propre = nom.trim();
        if (caisses.existsByNomIgnoreCaseAndSupprimeeFalse(propre)) {
            throw new ConflitException("La caisse " + propre + " existe déjà.");
        }
        Caisse caisse = caisses.save(new Caisse(propre));
        journal.enregistrer(adminId, ActionJournal.CAISSE_CREEE, propre);
        return CaisseDto.depuis(caisse);
    }

    @Transactional
    public CaisseDto modifier(long id, String nom, boolean active, long adminId) {
        String propre = nom.trim();
        Caisse caisse = charger(id);
        if (caisses.existsByNomIgnoreCaseAndIdNotAndSupprimeeFalse(propre, id)) {
            throw new ConflitException("La caisse " + propre + " existe déjà.");
        }
        boolean desactivee = caisse.isActive() && !active;
        caisse.modifier(propre, active);
        journal.enregistrer(adminId, ActionJournal.CAISSE_MODIFIEE, propre + (active ? "" : " (désactivée)"));
        if (desactivee) sessions.cloturerDOffice(id, adminId);
        return CaisseDto.depuis(caisse);
    }

    /**
     * Suppression logique d'une caisse inactive : retirée des écrans et des fiches utilisateurs,
     * son historique (sessions, ventes, journal) est conservé.
     */
    @Transactional
    public void supprimer(long id, long adminId) {
        Caisse caisse = charger(id);
        caisse.supprimer();
        utilisateurs.retirerCaisse(id);
        journal.enregistrer(adminId, ActionJournal.CAISSE_SUPPRIMEE, caisse.getNom());
    }

    /** Vérifie que toutes les caisses citées existent (affectation d'un utilisateur). */
    @Transactional(readOnly = true)
    public void verifierExistence(Set<Long> caisseIds) {
        if (!caisseIds.isEmpty() && caisses.countByIdInAndSupprimeeFalse(caisseIds) != caisseIds.size()) {
            throw new RessourceIntrouvableException("Caisse introuvable.");
        }
    }

    private Caisse charger(long id) {
        return caisses.findByIdAndSupprimeeFalse(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Caisse introuvable."));
    }

    public record CaisseDto(Long id, String nom, boolean active) {
        static CaisseDto depuis(Caisse caisse) {
            return new CaisseDto(caisse.getId(), caisse.getNom(), caisse.isActive());
        }
    }
}
