package ma.invento.pos.facture.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import ma.invento.pos.client.api.ClientDtos.ClientDto;
import ma.invento.pos.client.application.ClientService;
import ma.invento.pos.facture.api.FactureDtos.ClientFactureDto;
import ma.invento.pos.facture.api.FactureDtos.FactureDto;
import ma.invento.pos.facture.domain.ReglesFacture;
import ma.invento.pos.facture.infrastructure.FacturesJdbc;
import ma.invento.pos.facture.infrastructure.FacturesJdbc.Facture;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.parametre.application.ParametreService;
import ma.invento.pos.vente.api.VenteDtos.VenteDto;
import ma.invento.pos.vente.application.VenteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facture A4 d'une vente validée avec un client de l'annuaire. Le numéro est attribué à la première
 * impression (séquence annuelle sans trou) et l'identité du client y est figée ; les impressions
 * suivantes ressortent la même facture.
 */
@Service
public class FactureService {

    private final FacturesJdbc factures;
    private final VenteService ventes;
    private final ClientService clients;
    private final ParametreService parametres;
    private final JournalService journal;
    private final Clock clock;

    public FactureService(
            FacturesJdbc factures,
            VenteService ventes,
            ClientService clients,
            ParametreService parametres,
            JournalService journal,
            Clock clock) {
        this.factures = factures;
        this.ventes = ventes;
        this.clients = clients;
        this.parametres = parametres;
        this.journal = journal;
        this.clock = clock;
    }

    @Transactional
    public FactureDto facturer(long venteId, long utilisateurId) {
        factures.verrouillerVente(venteId);
        VenteDto vente = ventes.venteAFacturer(venteId);
        Facture facture = factures.parVente(venteId).orElseGet(() -> emettre(vente, utilisateurId));
        return new FactureDto(
                facture.numero(),
                facture.emiseLe(),
                new ClientFactureDto(
                        facture.clientType(),
                        facture.clientNom(),
                        facture.clientIce(),
                        facture.clientIdentifiantFiscal(),
                        facture.clientRc(),
                        facture.clientContact(),
                        facture.clientAdresse()),
                vente,
                ReglesFacture.montantEnLettres(vente.totaux().totalTtc()));
    }

    private Facture emettre(VenteDto vente, long utilisateurId) {
        ClientDto client = clients.fiche(vente.client().id());
        Instant maintenant = Instant.now(clock);
        int annee =
                maintenant.atZone(ZoneId.of(parametres.lire().fuseauHoraire())).getYear();
        String numero = ReglesFacture.numero(annee, factures.prochainNumero(annee));
        Facture facture = new Facture(
                numero,
                vente.id(),
                client.type(),
                client.nom(),
                client.ice(),
                client.identifiantFiscal(),
                client.rc(),
                client.contact(),
                client.adresse(),
                maintenant);
        factures.inserer(facture, utilisateurId);
        journal.enregistrer(
                utilisateurId,
                ActionJournal.FACTURE_EMISE,
                numero + " · vente " + vente.numero() + " · " + client.nom());
        return facture;
    }
}
