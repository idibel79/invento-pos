package ma.invento.pos.identite.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import ma.invento.pos.caisse.application.CaisseService;
import ma.invento.pos.identite.api.UtilisateurDtos.CreationUtilisateur;
import ma.invento.pos.identite.api.UtilisateurDtos.ModificationUtilisateur;
import ma.invento.pos.identite.api.UtilisateurDtos.UtilisateurDto;
import ma.invento.pos.identite.domain.PolitiqueSecrets;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.SeuilsRemise;
import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.identite.infrastructure.RefreshTokenRepository;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.shared.domain.ConflitException;
import ma.invento.pos.shared.domain.RegleMetierException;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import ma.invento.pos.tresorerie.application.TresorerieService;
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
    private final TresorerieService tresorerie;
    private final PasswordEncoder encoder;
    private final JournalService journal;
    private final Clock clock;

    public UtilisateurService(
            UtilisateurRepository utilisateurs,
            RefreshTokenRepository refreshTokens,
            CaisseService caisses,
            TresorerieService tresorerie,
            PasswordEncoder encoder,
            JournalService journal,
            Clock clock) {
        this.utilisateurs = utilisateurs;
        this.refreshTokens = refreshTokens;
        this.caisses = caisses;
        this.tresorerie = tresorerie;
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
        if (commande.pin() != null) PolitiqueSecrets.verifierPin(commande.pin());
        caisses.verifierExistence(commande.caisseIds());
        verifierCaissesLibres(commande.caisseIds(), null);
        Utilisateur utilisateur = new Utilisateur(
                commande.nom().trim(),
                commande.prenom().trim(),
                login,
                commande.role(),
                encoder.encode(commande.motDePasseTemporaire()),
                Instant.now(clock));
        utilisateur.modifierProfil(
                utilisateur.getNom(), utilisateur.getPrenom(), commande.role(), true, commande.caisseIds());
        utilisateur.definirSeuilsRemise(commande.seuilsRemise().versDomaine());
        if (commande.pin() != null) utilisateur.definirPin(encoder.encode(commande.pin()));
        utilisateurs.save(utilisateur);
        journal.enregistrer(adminId, ActionJournal.UTILISATEUR_CREE, login + " (" + commande.role() + ")");
        if (commande.pin() != null) journal.enregistrer(adminId, ActionJournal.PIN_DEFINI, login);
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
        String ancienLogin = utilisateur.getLogin();
        String login = commande.login().trim();
        boolean nouveauLogin = !login.equals(ancienLogin);
        if (nouveauLogin && !login.equalsIgnoreCase(ancienLogin) && utilisateurs.existsByLoginIgnoreCase(login)) {
            throw new ConflitException("L'identifiant " + login + " est déjà utilisé.");
        }
        String motDePasse = vide(commande.motDePasseTemporaire()) ? null : commande.motDePasseTemporaire();
        if (motDePasse != null) PolitiqueSecrets.verifierMotDePasse(login, motDePasse);
        if (commande.pin() != null) PolitiqueSecrets.verifierPin(commande.pin());
        caisses.verifierExistence(commande.caisseIds());
        if (utilisateur.getRole() != Role.ADMIN) verifierCaissesLibres(commande.caisseIds(), id);

        // L'argent dont il répond doit être soldé avant de désactiver un utilisateur.
        if (utilisateur.isActif() && !commande.actif()) tresorerie.verifierDesactivable(id);
        if (nouveauLogin) utilisateur.changerLogin(login);
        utilisateur.modifierProfil(
                commande.nom().trim(),
                commande.prenom().trim(),
                commande.role(),
                commande.actif(),
                commande.caisseIds());
        utilisateur.definirSeuilsRemise(commande.seuilsRemise().versDomaine());
        journal.enregistrer(
                adminId,
                ActionJournal.UTILISATEUR_MODIFIE,
                (nouveauLogin ? ancienLogin + " → " : "")
                        + login
                        + " ("
                        + commande.role()
                        + (commande.actif() ? "" : ", désactivé")
                        + seuils(utilisateur)
                        + ")");
        if (motDePasse != null) {
            // Mot de passe temporaire : déverrouille le compte et oblige à le changer.
            utilisateur.reinitialiserMotDePasse(encoder.encode(motDePasse));
            journal.enregistrer(adminId, ActionJournal.MOT_DE_PASSE_REINITIALISE, login);
        }
        if (commande.pin() != null) {
            utilisateur.definirPin(encoder.encode(commande.pin()));
            journal.enregistrer(adminId, ActionJournal.PIN_DEFINI, login);
        }
        if (!commande.actif() || motDePasse != null) refreshTokens.revoquerTous(id);
        return UtilisateurDto.depuis(utilisateurs.saveAndFlush(utilisateur));
    }

    /** Seuils de remise d'un responsable, pour le journal. */
    private static String seuils(Utilisateur utilisateur) {
        if (utilisateur.getRole() != Role.RESPONSABLE) return "";
        SeuilsRemise s = utilisateur.getSeuilsRemise();
        return ", remise max ligne " + s.ligne().stripTrailingZeros().toPlainString() + " %, ticket "
                + s.ticket().stripTrailingZeros().toPlainString() + " %";
    }

    private static boolean vide(String valeur) {
        return valeur == null || valeur.isEmpty();
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

    /** Une caisse n'est attribuée qu'à un seul utilisateur (index unique en base en dernier recours). */
    private void verifierCaissesLibres(Set<Long> caisseIds, Long utilisateurId) {
        for (long caisseId : caisseIds) {
            utilisateurs.findAttributaires(caisseId).stream()
                    .filter(u -> !u.getId().equals(utilisateurId))
                    .findFirst()
                    .ifPresent(u -> {
                        throw new ConflitException(
                                "Cette caisse est déjà attribuée à " + (u.getPrenom() + " " + u.getNom()).trim() + ".");
                    });
        }
    }

    private Utilisateur charger(long id) {
        return utilisateurs
                .findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));
    }
}
