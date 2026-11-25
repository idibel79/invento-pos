package ma.invento.pos.identite.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import ma.invento.pos.identite.api.UtilisateurDtos.UtilisateurDto;
import ma.invento.pos.identite.application.JetonService.Jetons;
import ma.invento.pos.identite.domain.GenerateurSecrets;
import ma.invento.pos.identite.domain.IdentifiantConnexion;
import ma.invento.pos.identite.domain.PolitiqueSecrets;
import ma.invento.pos.identite.domain.RefreshToken;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.identite.infrastructure.EssaisConnexionJdbc;
import ma.invento.pos.identite.infrastructure.EssaisConnexionJdbc.EssaisRates;
import ma.invento.pos.identite.infrastructure.RefreshTokenRepository;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.shared.domain.RegleMetierException;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Connexion, rafraîchissement, déconnexion, changement de mot de passe et code de secours. */
@Service
public class AuthService {

    private static final String IDENTIFIANTS_INVALIDES = "Identifiant ou mot de passe incorrect.";
    private static final String SESSION_INVALIDE = "Session expirée, reconnectez-vous.";

    /** Les essais restants s'affichent à partir de ce seuil (dès le 3e échec sur 5). */
    private static final int ESSAIS_RESTANTS_SIGNALES = 2;

    private final UtilisateurRepository utilisateurs;
    private final RefreshTokenRepository refreshTokens;
    private final JetonService jetons;
    private final CodeSecoursService codesSecours;
    private final LimiteurEssais limiteur;
    private final EssaisConnexionJdbc essaisConnexion;
    private final JournalService journal;
    private final PasswordEncoder encoder;
    private final Clock clock;

    /** Empreinte factice : même coût de vérification qu'un login existe ou non. */
    private final String empreinteFactice;

    public AuthService(
            UtilisateurRepository utilisateurs,
            RefreshTokenRepository refreshTokens,
            JetonService jetons,
            CodeSecoursService codesSecours,
            LimiteurEssais limiteur,
            EssaisConnexionJdbc essaisConnexion,
            JournalService journal,
            PasswordEncoder encoder,
            Clock clock) {
        this.utilisateurs = utilisateurs;
        this.refreshTokens = refreshTokens;
        this.jetons = jetons;
        this.codesSecours = codesSecours;
        this.limiteur = limiteur;
        this.essaisConnexion = essaisConnexion;
        this.journal = journal;
        this.encoder = encoder;
        this.clock = clock;
        this.empreinteFactice = encoder.encode("empreinte-factice");
    }

    /**
     * Les essais ratés sont comptés même si la connexion échoue (pas d'annulation sur 401/403). Un
     * identifiant inconnu ou désactivé reçoit exactement les mêmes réponses qu'un vrai compte
     * (essais restants, puis verrouillage) grâce à son propre compteur : les réponses ne révèlent
     * pas quels comptes existent. Le verrouillage dure {@link Utilisateur#DUREE_VERROUILLAGE} puis se
     * lève tout seul ; l'admin peut le lever avant.
     */
    @Transactional(noRollbackFor = {IdentifiantsInvalidesException.class, CompteVerrouilleException.class})
    public Session connecter(String login, String motDePasse, String adresseIp) {
        limiteur.verifier(limiteur.connexion(), adresseIp);
        Instant maintenant = Instant.now(clock);
        Utilisateur utilisateur = trouverParLogin(login);
        if (utilisateur == null || !utilisateur.isActif()) {
            encoder.matches(motDePasse, empreinteFactice);
            limiteur.enregistrerEchec(limiteur.connexion(), adresseIp);
            throw refus(essaisConnexion.ajouterEssaiInconnu(empreinteIdentifiant(login), maintenant), maintenant);
        }
        boolean motDePasseCorrect = encoder.matches(motDePasse, utilisateur.getMotDePasseHash());
        if (!motDePasseCorrect) {
            limiteur.enregistrerEchec(limiteur.connexion(), adresseIp);
        }
        if (utilisateur.isConnexionVerrouillee(maintenant)) {
            throw CompteVerrouilleException.connexion(utilisateur.getVerrouilleJusqua(), maintenant);
        }
        if (utilisateur.isVerrouille(maintenant)) {
            // PIN bloqué : seul l'admin le débloque.
            throw new CompteVerrouilleException();
        }
        if (!motDePasseCorrect) {
            throw refus(enregistrerEchec(utilisateur, maintenant), maintenant);
        }
        utilisateur.connexionReussie(maintenant);
        return ouvrirSession(utilisateur);
    }

    /**
     * Échange un jeton de rafraîchissement contre une nouvelle paire. Un jeton déjà utilisé
     * signale un vol probable : toutes les sessions de l'utilisateur sont alors fermées.
     */
    @Transactional(noRollbackFor = IdentifiantsInvalidesException.class)
    public Session rafraichir(String jetonRafraichissement) {
        RefreshToken jeton = trouverJeton(jetonRafraichissement);
        if (jeton.isRevoque()) {
            refreshTokens.revoquerTous(jeton.getUtilisateurId());
            throw new IdentifiantsInvalidesException(SESSION_INVALIDE);
        }
        if (jeton.estExpire(Instant.now(clock))) {
            throw new IdentifiantsInvalidesException(SESSION_INVALIDE);
        }
        jeton.revoquer();
        Instant maintenant = Instant.now(clock);
        Utilisateur utilisateur = utilisateurs
                .findById(jeton.getUtilisateurId())
                .filter(u -> u.peutSeConnecter(maintenant))
                .orElseThrow(() -> new IdentifiantsInvalidesException(SESSION_INVALIDE));
        return ouvrirSession(utilisateur);
    }

    @Transactional
    public void deconnecter(String jetonRafraichissement) {
        if (jetonRafraichissement == null || jetonRafraichissement.isBlank()) return;
        refreshTokens
                .findByTokenHash(GenerateurSecrets.empreinte(jetonRafraichissement))
                .ifPresent(RefreshToken::revoquer);
    }

    @Transactional
    public Session changerMotDePasse(long utilisateurId, String actuel, String nouveau) {
        Utilisateur utilisateur = charger(utilisateurId);
        if (!encoder.matches(actuel, utilisateur.getMotDePasseHash())) {
            throw new IdentifiantsInvalidesException("Mot de passe actuel incorrect.");
        }
        PolitiqueSecrets.verifierMotDePasse(utilisateur.getLogin(), nouveau);
        if (encoder.matches(nouveau, utilisateur.getMotDePasseHash())) {
            throw new RegleMetierException("Le nouveau mot de passe doit être différent de l'actuel.");
        }
        utilisateur.changerMotDePasse(encoder.encode(nouveau));
        refreshTokens.revoquerTous(utilisateurId);
        journal.enregistrer(utilisateurId, ActionJournal.MOT_DE_PASSE_CHANGE, null);
        return ouvrirSession(utilisateurs.getReferenceById(utilisateurId));
    }

    /**
     * Récupération de l'admin par son code de secours : nouveau mot de passe choisi, compte
     * déverrouillé, sessions fermées, et un nouveau code de secours renvoyé (affiché une fois).
     */
    @Transactional(noRollbackFor = IdentifiantsInvalidesException.class)
    public RecuperationAdmin recupererAdmin(String code, String nouveauMotDePasse, String adresseIp) {
        limiteur.verifier(limiteur.secours(), adresseIp);
        Utilisateur admin = utilisateurs
                .findByRole(Role.ADMIN)
                .orElseThrow(() -> new IdentifiantsInvalidesException("Code de secours invalide."));
        PolitiqueSecrets.verifierMotDePasse(admin.getLogin(), nouveauMotDePasse);
        if (!codesSecours.consommer(code)) {
            limiteur.enregistrerEchec(limiteur.secours(), adresseIp);
            throw new IdentifiantsInvalidesException("Code de secours invalide.");
        }
        admin.changerMotDePasse(encoder.encode(nouveauMotDePasse));
        admin.deverrouiller();
        admin.reactiver();
        refreshTokens.revoquerTous(admin.getId());
        String nouveauCode = codesSecours.renouveler();
        journal.enregistrer(admin.getId(), ActionJournal.CODE_SECOURS_UTILISE, "Mot de passe admin redéfini");
        return new RecuperationAdmin(admin.getLogin(), nouveauCode);
    }

    /** Nouveau code de secours demandé par l'admin connecté ; l'ancien cesse d'être valide. */
    @Transactional
    public String renouvelerCodeSecours(long adminId) {
        String code = codesSecours.renouveler();
        journal.enregistrer(adminId, ActionJournal.CODE_SECOURS_RENOUVELE, null);
        return code;
    }

    @Transactional(readOnly = true)
    public UtilisateurDto profil(long utilisateurId) {
        return UtilisateurDto.depuis(charger(utilisateurId), Instant.now(clock));
    }

    /**
     * L'identifiant tel que saisi, sinon sa forme normalisée : « Mohamed Alaoui » retrouve
     * {@code mohamed.alaoui}, et un ancien identifiant hors de cette forme reste utilisable.
     */
    private Utilisateur trouverParLogin(String saisie) {
        String brut = saisie.trim();
        return utilisateurs
                .findByLoginIgnoreCase(brut)
                .or(() -> {
                    String forme = IdentifiantConnexion.normaliser(brut);
                    return forme.isEmpty() || forme.equalsIgnoreCase(brut)
                            ? Optional.<Utilisateur>empty()
                            : utilisateurs.findByLoginIgnoreCase(forme);
                })
                .orElse(null);
    }

    /** Compte l'essai raté (mise à jour atomique en base) ; le dernier essai permis verrouille le compte. */
    private EssaisRates enregistrerEchec(Utilisateur utilisateur, Instant maintenant) {
        EssaisRates essais = essaisConnexion.ajouterEssaiCompte(utilisateur.getId(), maintenant);
        if (essais.verrouille(maintenant)) {
            journal.enregistrer(
                    utilisateur.getId(),
                    ActionJournal.COMPTE_VERROUILLE,
                    "Trop d'essais de connexion ratés (" + Utilisateur.DUREE_VERROUILLAGE.toMinutes() + " min)");
        }
        return essais;
    }

    /**
     * Refus après un essai raté : verrouillage au plafond, sinon le nombre d'essais restants quand il
     * n'en reste que {@value #ESSAIS_RESTANTS_SIGNALES} ou moins.
     */
    private static RuntimeException refus(EssaisRates essais, Instant maintenant) {
        if (essais.verrouille(maintenant)) {
            return CompteVerrouilleException.connexion(essais.verrouilleJusqua(), maintenant);
        }
        int restants = Utilisateur.ESSAIS_MAX - essais.essais();
        if (restants > ESSAIS_RESTANTS_SIGNALES) {
            return new IdentifiantsInvalidesException(IDENTIFIANTS_INVALIDES);
        }
        return new IdentifiantsInvalidesException(IDENTIFIANTS_INVALIDES + " Il vous reste " + restants
                + (restants == 1 ? " essai" : " essais") + " avant le verrouillage du compte.");
    }

    /** Empreinte de l'identifiant saisi, sous la même forme que la recherche du compte. */
    private static String empreinteIdentifiant(String saisie) {
        String forme = IdentifiantConnexion.normaliser(saisie.trim());
        return GenerateurSecrets.empreinte(
                forme.isEmpty() ? saisie.trim().toLowerCase(Locale.ROOT) : forme.toLowerCase(Locale.ROOT));
    }

    private Session ouvrirSession(Utilisateur utilisateur) {
        return new Session(jetons.emettre(utilisateur), UtilisateurDto.depuis(utilisateur, Instant.now(clock)));
    }

    private RefreshToken trouverJeton(String jetonRafraichissement) {
        if (jetonRafraichissement == null || jetonRafraichissement.isBlank()) {
            throw new IdentifiantsInvalidesException(SESSION_INVALIDE);
        }
        return refreshTokens
                .findByTokenHash(GenerateurSecrets.empreinte(jetonRafraichissement))
                .orElseThrow(() -> new IdentifiantsInvalidesException(SESSION_INVALIDE));
    }

    private Utilisateur charger(long id) {
        return utilisateurs
                .findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));
    }

    public record Session(Jetons jetons, UtilisateurDto utilisateur) {}

    public record RecuperationAdmin(String login, String nouveauCodeSecours) {}
}
