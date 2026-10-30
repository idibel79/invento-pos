package ma.invento.pos.identite.application;

import java.time.Clock;
import java.time.Instant;
import ma.invento.pos.identite.api.UtilisateurDtos.UtilisateurDto;
import ma.invento.pos.identite.application.JetonService.Jetons;
import ma.invento.pos.identite.domain.GenerateurSecrets;
import ma.invento.pos.identite.domain.PolitiqueSecrets;
import ma.invento.pos.identite.domain.RefreshToken;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.Utilisateur;
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

    private final UtilisateurRepository utilisateurs;
    private final RefreshTokenRepository refreshTokens;
    private final JetonService jetons;
    private final CodeSecoursService codesSecours;
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
            JournalService journal,
            PasswordEncoder encoder,
            Clock clock) {
        this.utilisateurs = utilisateurs;
        this.refreshTokens = refreshTokens;
        this.jetons = jetons;
        this.codesSecours = codesSecours;
        this.journal = journal;
        this.encoder = encoder;
        this.clock = clock;
        this.empreinteFactice = encoder.encode("empreinte-factice");
    }

    /** Les essais ratés sont comptés même si la connexion échoue (pas d'annulation sur 401/403). */
    @Transactional(noRollbackFor = {IdentifiantsInvalidesException.class, CompteVerrouilleException.class})
    public Session connecter(String login, String motDePasse) {
        Utilisateur utilisateur =
                utilisateurs.findByLoginIgnoreCase(login.trim()).orElse(null);
        if (utilisateur == null || !utilisateur.isActif()) {
            encoder.matches(motDePasse, empreinteFactice);
            throw new IdentifiantsInvalidesException(IDENTIFIANTS_INVALIDES);
        }
        if (!encoder.matches(motDePasse, utilisateur.getMotDePasseHash())) {
            enregistrerEchec(utilisateur);
            throw new IdentifiantsInvalidesException(IDENTIFIANTS_INVALIDES);
        }
        if (utilisateur.isVerrouille()) {
            throw new CompteVerrouilleException();
        }
        utilisateur.connexionReussie(Instant.now(clock));
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
        Utilisateur utilisateur = utilisateurs
                .findById(jeton.getUtilisateurId())
                .filter(Utilisateur::peutSeConnecter)
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
    @Transactional
    public RecuperationAdmin recupererAdmin(String code, String nouveauMotDePasse) {
        Utilisateur admin = utilisateurs
                .findByRole(Role.ADMIN)
                .orElseThrow(() -> new IdentifiantsInvalidesException("Code de secours invalide."));
        PolitiqueSecrets.verifierMotDePasse(admin.getLogin(), nouveauMotDePasse);
        if (!codesSecours.consommer(code)) {
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
        return UtilisateurDto.depuis(charger(utilisateurId));
    }

    private void enregistrerEchec(Utilisateur utilisateur) {
        if (utilisateur.isVerrouille()) return;
        utilisateurs.incrementerEssaisRates(utilisateur.getId());
        if (utilisateur.getEssaisRates() + 1 >= Utilisateur.ESSAIS_MAX) {
            journal.enregistrer(
                    utilisateur.getId(), ActionJournal.COMPTE_VERROUILLE, "Trop d'essais de connexion ratés");
        }
    }

    private Session ouvrirSession(Utilisateur utilisateur) {
        return new Session(jetons.emettre(utilisateur), UtilisateurDto.depuis(utilisateur));
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
