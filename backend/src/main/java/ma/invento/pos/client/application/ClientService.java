package ma.invento.pos.client.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import ma.invento.pos.client.api.ClientDtos.ClientCommande;
import ma.invento.pos.client.api.ClientDtos.ClientDto;
import ma.invento.pos.client.api.ClientDtos.MouvementClientDto;
import ma.invento.pos.client.api.ClientDtos.RemboursementCommande;
import ma.invento.pos.client.domain.ReglesClient;
import ma.invento.pos.client.domain.TypeMouvementClient;
import ma.invento.pos.client.infrastructure.ClientsJdbc;
import ma.invento.pos.client.infrastructure.ClientsJdbc.Client;
import ma.invento.pos.client.infrastructure.ClientsJdbc.Mouvement;
import ma.invento.pos.client.infrastructure.ClientsJdbc.NouveauMouvement;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.shared.domain.ConflitException;
import ma.invento.pos.shared.domain.RegleMetierException;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import ma.invento.pos.tresorerie.application.TresorerieService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Annuaire des clients (géré par l'admin, consulté par tous) et leur dette : ventes à crédit,
 * remboursements encaissés par le caissier (son compte est crédité dans la même transaction) et
 * annulation d'un remboursement par l'admin.
 */
@Service
public class ClientService {

    private final ClientsJdbc clients;
    private final TresorerieService tresorerie;
    private final JournalService journal;
    private final Clock clock;

    public ClientService(ClientsJdbc clients, TresorerieService tresorerie, JournalService journal, Clock clock) {
        this.clients = clients;
        this.tresorerie = tresorerie;
        this.journal = journal;
        this.clock = clock;
    }

    /** Nom du client et sa dette juste après une vente à crédit. */
    public record Dette(String nom, BigDecimal apres) {}

    // ---------------------------------------------------------------- Annuaire

    @Transactional(readOnly = true)
    public List<ClientDto> lister(String recherche, boolean inactifs) {
        return clients.lister(recherche, inactifs).stream()
                .map(ClientDto::depuis)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MouvementClientDto> releve(long clientId) {
        charger(clientId);
        return clients.releve(clientId).stream().map(MouvementClientDto::depuis).toList();
    }

    @Transactional
    public ClientDto creer(ClientCommande commande, long adminId) {
        String nom = ReglesClient.nom(commande.nom());
        if (clients.nomActifPris(nom, null)) throw new ConflitException("Le client " + nom + " existe déjà.");
        long id = clients.inserer(
                nom,
                ReglesClient.facultatif(commande.telephone(), ReglesClient.TELEPHONE_MAX, "Le téléphone"),
                ReglesClient.facultatif(commande.note(), ReglesClient.NOTE_MAX, "La note"),
                ReglesClient.plafond(commande.plafond()),
                Instant.now(clock));
        journal.enregistrer(adminId, ActionJournal.CLIENT_CREE, nom);
        return ClientDto.depuis(charger(id));
    }

    @Transactional
    public ClientDto modifier(long id, ClientCommande commande, long adminId) {
        charger(id);
        String nom = ReglesClient.nom(commande.nom());
        boolean actif = commande.actif() == null || commande.actif();
        if (actif && clients.nomActifPris(nom, id)) throw new ConflitException("Le client " + nom + " existe déjà.");
        clients.modifier(
                id,
                nom,
                ReglesClient.facultatif(commande.telephone(), ReglesClient.TELEPHONE_MAX, "Le téléphone"),
                ReglesClient.facultatif(commande.note(), ReglesClient.NOTE_MAX, "La note"),
                ReglesClient.plafond(commande.plafond()),
                actif);
        journal.enregistrer(adminId, ActionJournal.CLIENT_MODIFIE, nom + (actif ? "" : " (désactivé)"));
        return ClientDto.depuis(charger(id));
    }

    /**
     * Supprime un client qui n'a jamais rien acheté ; un client avec un historique est seulement désactivé
     * (il n'est plus proposé à la caisse, son relevé et sa dette restent).
     *
     * @return vrai si le client a vraiment été supprimé
     */
    @Transactional
    public boolean supprimer(long id, long adminId) {
        Client client = charger(id);
        if (clients.aUnHistorique(id)) {
            clients.modifier(id, client.nom(), client.telephone(), client.note(), client.plafond(), false);
            journal.enregistrer(adminId, ActionJournal.CLIENT_MODIFIE, client.nom() + " (désactivé)");
            return false;
        }
        clients.supprimer(id);
        journal.enregistrer(adminId, ActionJournal.CLIENT_SUPPRIME, client.nom());
        return true;
    }

    // ---------------------------------------------------------------- Ventes

    /** Nom d'un client actif, pour l'imprimer sur le ticket d'une vente payée comptant. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nomPourVente(long clientId) {
        return clients.parId(clientId)
                .filter(Client::actif)
                .map(Client::nom)
                .orElseThrow(() -> new RessourceIntrouvableException("Client introuvable ou désactivé."));
    }

    /**
     * Vente à crédit, dans la transaction de la vente : la dette du client augmente du total. À compléter
     * par {@link #inscrireVenteACredit} une fois la vente enregistrée.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Dette vendreACredit(long clientId, BigDecimal total) {
        ReglesClient.verifierVenteACredit(total);
        return clients.ajouterDette(clientId, total)
                .map(d -> new Dette(d.nom(), d.apres()))
                .orElseThrow(() -> refusCredit(clientId, total));
    }

    /** Pourquoi la dette n'a pas pu augmenter : client absent ou désactivé, sinon plafond dépassé. */
    private RuntimeException refusCredit(long clientId, BigDecimal total) {
        return clients.parId(clientId)
                .filter(Client::actif)
                .<RuntimeException>map(c -> new RegleMetierException("Plafond de crédit dépassé pour " + c.nom()
                        + " : dette " + c.dette().toPlainString() + " DH + " + total.toPlainString()
                        + " DH dépasserait le plafond de " + c.plafond().toPlainString() + " DH."))
                .orElseGet(() -> new RessourceIntrouvableException("Client introuvable ou désactivé."));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void inscrireVenteACredit(
            long clientId, long venteId, BigDecimal total, BigDecimal detteApres, long vendeurId, Long sessionId) {
        clients.insererMouvement(new NouveauMouvement(
                clientId,
                TypeMouvementClient.VENTE_CREDIT,
                total,
                null,
                null,
                venteId,
                null,
                vendeurId,
                sessionId,
                false,
                null,
                detteApres,
                null,
                Instant.now(clock)));
    }

    // ---------------------------------------------------------------- Remboursements

    /**
     * Le client rembourse tout ou partie de sa dette à qui l'encaisse : sa dette baisse et le compte de
     * l'encaisseur monte du même montant (la caisse principale pour l'admin), dans la même transaction.
     */
    @Transactional
    public MouvementClientDto rembourser(
            long clientId, RemboursementCommande commande, UtilisateurConnecte utilisateur) {
        if (commande.cle() != null) {
            var deja = clients.parCle(commande.cle());
            if (deja.isPresent()) return dejaRembourse(deja.get(), clientId, utilisateur);
        }
        Client client = charger(clientId);
        BigDecimal total = ReglesClient.remboursement(commande.especes(), commande.tpe());
        ClientsJdbc.Dette dette = clients.retirerDette(client.id(), total);
        Long sessionId = tresorerie.encaisserDette(utilisateur, total, "Dette · " + dette.nom());
        long id = clients.insererMouvement(new NouveauMouvement(
                clientId,
                TypeMouvementClient.REMBOURSEMENT,
                total.negate(),
                zeroSiNull(commande.especes()),
                zeroSiNull(commande.tpe()),
                null,
                null,
                utilisateur.id(),
                sessionId,
                utilisateur.estAdmin(),
                null,
                dette.apres(),
                commande.cle(),
                Instant.now(clock)));
        journal.enregistrer(
                utilisateur.id(),
                ActionJournal.DETTE_ENCAISSEE,
                dette.nom() + " : " + total.toPlainString() + " DH, reste "
                        + dette.apres().toPlainString() + " DH");
        return MouvementClientDto.depuis(clients.mouvement(id).orElseThrow());
    }

    /**
     * Annulation par l'admin d'un remboursement saisi par erreur : la dette remonte et le compte qui
     * avait reçu l'argent est débité. Une ligne inverse est ajoutée au relevé, rien n'est effacé.
     */
    @Transactional
    public MouvementClientDto annulerRemboursement(long remboursementId, String motif, long adminId) {
        String propre = ReglesClient.facultatif(motif, ReglesClient.MOTIF_MAX, "Le motif");
        if (propre == null) throw new RegleMetierException("Le motif est obligatoire.");
        Mouvement remboursement = clients.mouvement(remboursementId)
                .filter(m -> m.type() == TypeMouvementClient.REMBOURSEMENT)
                .orElseThrow(() -> new RessourceIntrouvableException("Remboursement introuvable."));
        if (!clients.marquerAnnule(remboursementId)) {
            throw new ConflitException("Ce remboursement est déjà annulé.");
        }
        BigDecimal montant = remboursement.montant().negate();
        ClientsJdbc.Dette dette = clients.remonterDette(remboursement.clientId(), montant);
        tresorerie.annulerDette(
                remboursement.utilisateurId(),
                remboursement.caissePrincipale(),
                montant,
                "Annulation dette · " + dette.nom() + " : " + propre,
                adminId);
        long id = clients.insererMouvement(new NouveauMouvement(
                remboursement.clientId(),
                TypeMouvementClient.ANNULATION_REMBOURSEMENT,
                montant,
                remboursement.especes(),
                remboursement.tpe(),
                null,
                remboursementId,
                adminId,
                null,
                remboursement.caissePrincipale(),
                propre,
                dette.apres(),
                null,
                Instant.now(clock)));
        journal.enregistrer(
                adminId,
                ActionJournal.DETTE_REMBOURSEMENT_ANNULE,
                dette.nom() + " : " + montant.toPlainString() + " DH (" + remboursement.utilisateur() + "), " + propre);
        return MouvementClientDto.depuis(clients.mouvement(id).orElseThrow());
    }

    private static MouvementClientDto dejaRembourse(Mouvement deja, long clientId, UtilisateurConnecte utilisateur) {
        if (deja.clientId() != clientId || deja.utilisateurId() != utilisateur.id()) {
            throw new ConflitException("Clé d'idempotence déjà utilisée.");
        }
        return MouvementClientDto.depuis(deja);
    }

    private static BigDecimal zeroSiNull(BigDecimal montant) {
        return montant == null ? BigDecimal.ZERO : montant;
    }

    private Client charger(long id) {
        return clients.parId(id).orElseThrow(() -> new RessourceIntrouvableException("Client introuvable."));
    }
}
