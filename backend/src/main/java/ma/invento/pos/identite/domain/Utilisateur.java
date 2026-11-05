package ma.invento.pos.identite.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import ma.invento.pos.shared.domain.RegleMetierException;

/** Compte d'un caissier, d'un responsable ou de l'admin. Jamais supprimé, seulement désactivé. */
@Entity
@Table(name = "utilisateur")
public class Utilisateur {

    /** Nombre d'essais ratés (mot de passe ou PIN) qui verrouille le compte. */
    public static final int ESSAIS_MAX = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "utilisateur_seq")
    @SequenceGenerator(name = "utilisateur_seq", sequenceName = "utilisateur_seq", allocationSize = 50)
    private Long id;

    private String nom;
    private String prenom;
    private String login;

    @Column(name = "mot_de_passe_hash")
    private String motDePasseHash;

    private String pinHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean actif = true;

    @Column(name = "doit_changer_mdp")
    private boolean doitChangerMotDePasse = true;

    private int essaisRates;
    private int essaisPinRates;
    private Instant derniereConnexion;

    @Version
    private long version;

    private Instant creeLe;

    @ElementCollection
    @CollectionTable(name = "utilisateur_caisse", joinColumns = @JoinColumn(name = "utilisateur_id"))
    @Column(name = "caisse_id")
    private Set<Long> caisseIds = new HashSet<>();

    /** Seuils de remise d'un responsable, en % (0 pour les autres rôles ; l'admin n'a pas de limite). */
    private BigDecimal remiseMaxLigne = BigDecimal.ZERO;

    private BigDecimal remiseMaxTicket = BigDecimal.ZERO;

    protected Utilisateur() {}

    /** Nouveau compte avec un mot de passe temporaire, à changer à la première connexion. */
    public Utilisateur(
            String nom, String prenom, String login, Role role, String motDePasseTemporaireHash, Instant creeLe) {
        this.nom = nom;
        this.prenom = prenom;
        this.login = login;
        this.role = role;
        this.motDePasseHash = motDePasseTemporaireHash;
        this.creeLe = creeLe;
    }

    public boolean isVerrouille() {
        return essaisRates >= ESSAIS_MAX || essaisPinRates >= ESSAIS_MAX;
    }

    public boolean peutSeConnecter() {
        return actif && !isVerrouille();
    }

    public void connexionReussie(Instant maintenant) {
        this.essaisRates = 0;
        this.derniereConnexion = maintenant;
    }

    /** Mot de passe choisi par l'utilisateur lui-même. */
    public void changerMotDePasse(String nouveauHash) {
        this.motDePasseHash = nouveauHash;
        this.doitChangerMotDePasse = false;
    }

    /** Mot de passe temporaire (admin, commande serveur) : déverrouille et oblige à le changer. */
    public void reinitialiserMotDePasse(String hashTemporaire) {
        this.motDePasseHash = hashTemporaire;
        this.doitChangerMotDePasse = true;
        deverrouiller();
    }

    public void deverrouiller() {
        this.essaisRates = 0;
        this.essaisPinRates = 0;
    }

    /** Le PIN sert à autoriser une remise : un caissier n'en a pas. */
    public void definirPin(String pinHash) {
        if (role == Role.CAISSIER) {
            throw new RegleMetierException("Un caissier n'a pas de PIN : il n'accorde pas de remise.");
        }
        this.pinHash = pinHash;
        this.essaisPinRates = 0;
    }

    /** PIN correct : le compteur d'essais ratés repart de zéro. */
    public void pinReussi() {
        this.essaisPinRates = 0;
    }

    /** Une caisse au plus par utilisateur ; l'admin ouvre toutes les caisses, il n'en a pas d'attribuée. */
    public void modifierProfil(String nom, String prenom, Role role, boolean actif, Set<Long> caisseIds) {
        if (caisseIds.size() > 1) {
            throw new RegleMetierException("Un utilisateur ne peut avoir qu'une seule caisse attribuée.");
        }
        this.nom = nom;
        this.prenom = prenom;
        this.role = role;
        this.actif = actif;
        this.caisseIds.clear();
        if (role != Role.ADMIN) this.caisseIds.addAll(caisseIds);
        if (role == Role.CAISSIER) {
            // Un caissier n'accorde aucune remise : ni PIN, ni seuils.
            this.pinHash = null;
            this.essaisPinRates = 0;
            definirSeuilsRemise(SeuilsRemise.AUCUN);
        }
    }

    /** Seuils fixés par l'admin ; seul un responsable en a (l'admin n'a pas de limite). */
    public void definirSeuilsRemise(SeuilsRemise seuils) {
        SeuilsRemise retenus = role == Role.RESPONSABLE ? seuils : SeuilsRemise.AUCUN;
        this.remiseMaxLigne = retenus.ligne();
        this.remiseMaxTicket = retenus.ticket();
    }

    public SeuilsRemise getSeuilsRemise() {
        return new SeuilsRemise(remiseMaxLigne, remiseMaxTicket);
    }

    /** Nouvel identifiant de connexion (unicité vérifiée par le service, contrainte unique en base). */
    public void changerLogin(String login) {
        this.login = login;
    }

    /** Réactive le compte admin (récupération). */
    public void reactiver() {
        this.actif = true;
    }

    public Long getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public String getLogin() {
        return login;
    }

    public String getMotDePasseHash() {
        return motDePasseHash;
    }

    public String getPinHash() {
        return pinHash;
    }

    public int getEssaisPinRates() {
        return essaisPinRates;
    }

    public boolean isPinDefini() {
        return pinHash != null;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActif() {
        return actif;
    }

    public boolean isDoitChangerMotDePasse() {
        return doitChangerMotDePasse;
    }

    public int getEssaisRates() {
        return essaisRates;
    }

    public Instant getDerniereConnexion() {
        return derniereConnexion;
    }

    public long getVersion() {
        return version;
    }

    public Set<Long> getCaisseIds() {
        return Set.copyOf(caisseIds);
    }
}
