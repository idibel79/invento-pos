package ma.invento.pos.identite.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import ma.invento.pos.caisse.application.CaisseService;
import ma.invento.pos.identite.api.UtilisateurDtos.CreationUtilisateur;
import ma.invento.pos.identite.api.UtilisateurDtos.ModificationUtilisateur;
import ma.invento.pos.identite.api.UtilisateurDtos.UtilisateurDto;
import ma.invento.pos.identite.domain.PolitiqueSecrets;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.identite.infrastructure.RefreshTokenRepository;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.shared.domain.ConflitException;
import ma.invento.pos.shared.domain.RegleMetierException;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestion des comptes par l'admin (cadrage § 5, Utilisateurs et administration). */
@Service
public class UtilisateurService {

    private final UtilisateurRepository utilisateurs;
    private final RefreshTokenRepository refreshTokens;
    private final CaisseService caisses;
    private final PasswordEncoder encoder;
    private final JournalService journal;
    private final Clock clock;

    public UtilisateurService(
            UtilisateurRepository utilisateurs,
            RefreshTokenRepository refreshTokens,
            CaisseService caisses,
            PasswordEncoder encoder,
            JournalService journal,
            Clock clock) {
        this.utilisateurs = utilisateurs;
        this.refreshTokens = refreshTokens;
        this.caisses = caisses;
        this.encoder = encoder;
        this.journal = journal;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UtilisateurDto> lister() {
        return utilisateurs.findAllByOrderByNomAscPrenomAsc().stream()
                .map(UtilisateurDto::depuis)
                .toList();
    }

    @Transactional
    public UtilisateurDto creer(CreationUtilisateur commande, long adminId) {
        refuserAdmin(commande.role());
        String login = commande.login().trim();
        if (utilisateurs.existsByLoginIgnoreCase(login)) {
            throw new ConflitException("L'identifiant " + login + " est déjà utilisé.");
        }
        PolitiqueSecrets.verifierMotDePasse(login, commande.motDePasseTemporaire());
        caisses.verifierExistence(commande.caisseIds());
        Utilisateur utilisateur = new Utilisateur(
                commande.nom().trim(),
                commande.prenom().trim(),
                login,
                commande.role(),
                encoder.encode(commande.motDePasseTemporaire()),
                Instant.now(clock));
        utilisateur.modifierProfil(
                utilisateur.getNom(), utilisateur.getPrenom(), commande.role(), true, commande.caisseIds());
        utilisateurs.save(utilisateur);
        journal.enregistrer(adminId, ActionJournal.UTILISATEUR_CREE, login + " (" + commande.role() + ")");
        return UtilisateurDto.depuis(utilisateur);
    }

    @Transactional
    public UtilisateurDto modifier(long id, ModificationUtilisateur commande, long adminId) {
        Utilisateur utilisateur = charger(id);
        if (commande.version() != utilisateur.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Utilisateur.class, id);
        }
        if (utilisateur.getRole() == Role.ADMIN) {
            if (commande.role() != Role.ADMIN || !commande.actif()) {
                throw new RegleMetierException("Le compte admin ne peut être ni rétrogradé ni désactivé.");
            }
        } else {
            refuserAdmin(commande.role());
        }
        caisses.verifierExistence(commande.caisseIds());
        utilisateur.modifierProfil(
                commande.nom().trim(),
                commande.prenom().trim(),
                commande.role(),
                commande.actif(),
                commande.caisseIds());
        if (!commande.actif()) refreshTokens.revoquerTous(id);
        journal.enregistrer(
                adminId,
                ActionJournal.UTILISATEUR_MODIFIE,
                utilisateur.getLogin() + " (" + commande.role() + (commande.actif() ? ")" : ", désactivé)"));
        return UtilisateurDto.depuis(charger(id));
    }

    /** Mot de passe temporaire défini par l'admin : déverrouille et oblige à le changer. */
    @Transactional
    public void reinitialiserMotDePasse(long id, String motDePasseTemporaire, long adminId) {
        Utilisateur utilisateur = charger(id);
        PolitiqueSecrets.verifierMotDePasse(utilisateur.getLogin(), motDePasseTemporaire);
        utilisateur.reinitialiserMotDePasse(encoder.encode(motDePasseTemporaire));
        refreshTokens.revoquerTous(id);
        journal.enregistrer(adminId, ActionJournal.MOT_DE_PASSE_REINITIALISE, utilisateur.getLogin());
    }

    @Transactional
    public void deverrouiller(long id, long adminId) {
        Utilisateur utilisateur = charger(id);
        utilisateur.deverrouiller();
        journal.enregistrer(adminId, ActionJournal.UTILISATEUR_MODIFIE, utilisateur.getLogin() + " déverrouillé");
    }

    @Transactional
    public void definirPin(long id, String pin, long adminId) {
        PolitiqueSecrets.verifierPin(pin);
        Utilisateur utilisateur = charger(id);
        utilisateur.definirPin(encoder.encode(pin));
        journal.enregistrer(adminId, ActionJournal.PIN_DEFINI, utilisateur.getLogin());
    }

    /** Un seul admin, créé à l'installation (cadrage § 1). */
    private static void refuserAdmin(Role role) {
        if (role == Role.ADMIN) {
            throw new RegleMetierException("Il ne peut y avoir qu'un seul compte admin.");
        }
    }

    private Utilisateur charger(long id) {
        return utilisateurs
                .findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));
    }
}
