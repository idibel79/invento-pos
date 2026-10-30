package ma.invento.pos.vente.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import ma.invento.pos.shared.domain.RegleMetierException;
import ma.invento.pos.vente.domain.PanierCalcule.LigneCalculee;

/**
 * Ticket de caisse. Une vente validée ne change plus ; une vente en attente garde son panier
 * (prix et remises) et le stock réservé jusqu'à sa reprise, son annulation ou son échéance.
 */
@Entity
@Table(name = "vente")
public class Vente {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vente_seq")
    @SequenceGenerator(name = "vente_seq", sequenceName = "vente_seq", allocationSize = 50)
    private Long id;

    private String numero;
    private String cleIdempotence;
    private Long caisseId;
    private Long caissierId;
    private Long sessionCaisseId;

    @Enumerated(EnumType.STRING)
    private StatutVente statut;

    private BigDecimal totalHt;
    private BigDecimal totalTva;
    private BigDecimal totalTtc;

    @Enumerated(EnumType.STRING)
    private TypeRemise remiseTicketType;

    private BigDecimal remiseTicketValeur;
    private BigDecimal remiseTicketMontant;
    private String motifRemise;
    private Long valideePar;
    private String noteAttente;
    private Instant attenteExpireLe;
    private Instant creeLe;
    private Instant finaliseeLe;

    @Version
    private long version;

    @OneToMany(mappedBy = "vente", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<LigneVente> lignes = new ArrayList<>();

    @OneToMany(mappedBy = "vente", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Paiement> paiements = new ArrayList<>();

    protected Vente() {}

    public Vente(
            String numero,
            String cleIdempotence,
            long caisseId,
            long caissierId,
            long sessionCaisseId,
            Instant creeLe) {
        this.numero = numero;
        this.cleIdempotence = cleIdempotence;
        this.caisseId = caisseId;
        this.caissierId = caissierId;
        this.sessionCaisseId = sessionCaisseId;
        this.creeLe = creeLe;
    }

    /** Remplace le panier par celui calculé (lignes, remises, totaux). */
    public void remplirPanier(PanierCalcule panier, Remise remiseTicket, String motifRemise, Long valideePar) {
        verifierModifiable();
        lignes.clear();
        for (LigneCalculee ligne : panier.lignes()) {
            lignes.add(new LigneVente(this, ligne));
        }
        this.totalHt = panier.totalHt();
        this.totalTva = panier.totalTva();
        this.totalTtc = panier.totalTtc();
        this.remiseTicketType = remiseTicket == null ? null : remiseTicket.type();
        this.remiseTicketValeur = remiseTicket == null ? null : remiseTicket.valeur();
        this.remiseTicketMontant = remiseTicket == null ? null : panier.remiseTicket();
        this.motifRemise = motifRemise;
        this.valideePar = valideePar;
    }

    public void mettreEnAttente(String note, Instant expireLe) {
        verifierModifiable();
        this.statut = StatutVente.EN_ATTENTE;
        this.noteAttente = note;
        this.attenteExpireLe = expireLe;
    }

    /**
     * Encaissement en espèces : le montant reçu couvre le total, la monnaie est rendue. La clé
     * d'idempotence devient celle de la demande de validation (une vente reprise en avait une autre).
     */
    public void validerEnEspeces(BigDecimal especesRecues, String cleIdempotence, Instant maintenant) {
        verifierModifiable();
        if (especesRecues.compareTo(totalTtc) < 0) {
            throw new RegleMetierException("Le montant reçu ne couvre pas le total de la vente.");
        }
        if (totalTtc.signum() > 0) {
            paiements.add(new Paiement(this, ModePaiement.ESPECES, totalTtc, especesRecues));
        }
        this.statut = StatutVente.VALIDEE;
        this.cleIdempotence = cleIdempotence;
        this.noteAttente = null;
        this.attenteExpireLe = null;
        this.finaliseeLe = maintenant;
    }

    /** Reprise sur la caisse : le client est revenu, la réservation repart pour la durée d'attente. */
    public void prolongerAttente(Instant expireLe) {
        if (statut != StatutVente.EN_ATTENTE) {
            throw new RegleMetierException("Cette vente n'est plus en attente.");
        }
        this.attenteExpireLe = expireLe;
    }

    /** Abandon d'une vente en attente (caissier, échéance ou clôture de la session). */
    public void annulerAttente(Instant maintenant) {
        if (statut != StatutVente.EN_ATTENTE) {
            throw new RegleMetierException("Seule une vente en attente peut être abandonnée.");
        }
        this.statut = StatutVente.ANNULEE;
        this.attenteExpireLe = null;
        this.finaliseeLe = maintenant;
    }

    public boolean estEnAttente() {
        return statut == StatutVente.EN_ATTENTE;
    }

    public boolean estExpiree(Instant maintenant) {
        return estEnAttente() && !attenteExpireLe.isAfter(maintenant);
    }

    private void verifierModifiable() {
        if (statut == StatutVente.VALIDEE || statut == StatutVente.ANNULEE) {
            throw new RegleMetierException(
                    "Cette vente est déjà " + (statut == StatutVente.VALIDEE ? "validée." : "annulée."));
        }
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public String getCleIdempotence() {
        return cleIdempotence;
    }

    public Long getCaisseId() {
        return caisseId;
    }

    public Long getCaissierId() {
        return caissierId;
    }

    public Long getSessionCaisseId() {
        return sessionCaisseId;
    }

    public StatutVente getStatut() {
        return statut;
    }

    public BigDecimal getTotalHt() {
        return totalHt;
    }

    public BigDecimal getTotalTva() {
        return totalTva;
    }

    public BigDecimal getTotalTtc() {
        return totalTtc;
    }

    public TypeRemise getRemiseTicketType() {
        return remiseTicketType;
    }

    public BigDecimal getRemiseTicketValeur() {
        return remiseTicketValeur;
    }

    public Remise getRemiseTicket() {
        return Remise.ou(remiseTicketType, remiseTicketValeur);
    }

    public String getMotifRemise() {
        return motifRemise;
    }

    public Long getValideePar() {
        return valideePar;
    }

    public String getNoteAttente() {
        return noteAttente;
    }

    public Instant getAttenteExpireLe() {
        return attenteExpireLe;
    }

    public Instant getCreeLe() {
        return creeLe;
    }

    public Instant getFinaliseeLe() {
        return finaliseeLe;
    }

    public List<LigneVente> getLignes() {
        return List.copyOf(lignes);
    }

    public List<Paiement> getPaiements() {
        return List.copyOf(paiements);
    }
}
