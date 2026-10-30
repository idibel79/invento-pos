package ma.invento.pos.journal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "journal_action")
public class JournalAction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "journal_action_seq")
    @SequenceGenerator(name = "journal_action_seq", sequenceName = "journal_action_seq", allocationSize = 50)
    private Long id;

    /** Auteur de l'action ; vide pour une action du système (installation, commande serveur). */
    private Long utilisateurId;

    @Enumerated(EnumType.STRING)
    private ActionJournal action;

    private String detail;
    private Instant creeLe;

    protected JournalAction() {}

    public JournalAction(Long utilisateurId, ActionJournal action, String detail, Instant creeLe) {
        this.utilisateurId = utilisateurId;
        this.action = action;
        this.detail = detail;
        this.creeLe = creeLe;
    }

    public Long getId() {
        return id;
    }

    public Long getUtilisateurId() {
        return utilisateurId;
    }

    public ActionJournal getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreeLe() {
        return creeLe;
    }
}
