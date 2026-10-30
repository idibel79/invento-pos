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
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

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

    public void definirPin(String pinHash) {
        this.pinHash = pinHash;
        this.essaisPinRates = 0;
    }

    /** PIN correct : le compteur d'essais ratés repart de zéro. */
    public void pinReussi() {
        this.essaisPinRates = 0;
    }

    public void modifierProfil(String nom, String prenom, Role role, boolean actif, Set<Long> caisseIds) {
        this.nom = nom;
        this.prenom = prenom;
        this.role = role;
        this.actif = actif;
        this.caisseIds.clear();
        this.caisseIds.addAll(caisseIds);
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
