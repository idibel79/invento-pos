package ma.invento.pos.client.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.invento.pos.client.domain.FicheClient;
import ma.invento.pos.client.domain.ReglesClient;
import ma.invento.pos.client.domain.TypeClient;
import ma.invento.pos.client.domain.TypeMouvementClient;
import ma.invento.pos.client.infrastructure.ClientsJdbc.Client;
import ma.invento.pos.client.infrastructure.ClientsJdbc.Mouvement;

/** Contrats de l'API des clients. */
public final class ClientDtos {

    private ClientDtos() {}

    /** @param nom nom et prénom, ou raison sociale d'une entreprise */
    public record ClientDto(
            Long id,
            TypeClient type,
            String nom,
            @Schema(nullable = true) String telephone,
            @Schema(nullable = true) String note,
            @Schema(nullable = true) String ice,
            @Schema(nullable = true) String identifiantFiscal,
            @Schema(nullable = true) String rc,
            @Schema(nullable = true) String contact,
            @Schema(nullable = true) String adresse,
            @Schema(nullable = true) String email,
            boolean actif,
            BigDecimal dette,

            @Schema(nullable = true, description = "Plafond de crédit, sans limite si absent")
            BigDecimal plafond,

            @Schema(nullable = true) Instant dernierMouvement) {

        public static ClientDto depuis(Client c) {
            return new ClientDto(
                    c.id(),
                    c.type(),
                    c.nom(),
                    c.telephone(),
                    c.note(),
                    c.ice(),
                    c.identifiantFiscal(),
                    c.rc(),
                    c.contact(),
                    c.adresse(),
                    c.email(),
                    c.actif(),
                    c.dette(),
                    c.plafond(),
                    c.dernierMouvement());
        }
    }

    /**
     * Ligne du relevé. {@code montant} est signé : positif quand la dette augmente.
     *
     * @param caissePrincipale remboursement encaissé par l'admin, entré dans la caisse principale
     * @param annule remboursement annulé par l'admin (une ligne d'annulation le suit)
     */
    public record MouvementClientDto(
            Long id,
            Long clientId,
            String client,
            TypeMouvementClient type,
            BigDecimal montant,
            @Schema(nullable = true) BigDecimal especes,
            @Schema(nullable = true) BigDecimal tpe,
            @Schema(nullable = true) Long venteId,
            @Schema(nullable = true) String venteNumero,
            @Schema(nullable = true) Long remboursementId,
            boolean annule,
            String utilisateur,
            boolean caissePrincipale,
            @Schema(nullable = true) String motif,
            BigDecimal detteApres,
            Instant le) {

        public static MouvementClientDto depuis(Mouvement m) {
            return new MouvementClientDto(
                    m.id(),
                    m.clientId(),
                    m.client(),
                    m.type(),
                    m.montant(),
                    m.especes(),
                    m.tpe(),
                    m.venteId(),
                    m.venteNumero(),
                    m.remboursementId(),
                    m.annule(),
                    m.utilisateur(),
                    m.caissePrincipale(),
                    m.motif(),
                    m.detteApres(),
                    m.creeLe());
        }
    }

    /**
     * Création ou modification d'un client. Les champs ICE à email ne valent que pour une entreprise ; ils
     * sont ignorés pour un particulier.
     *
     * @param type particulier si absent
     * @param nom nom et prénom, ou raison sociale d'une entreprise
     */
    public record ClientCommande(
            @Schema(nullable = true) TypeClient type,
            @NotBlank @Size(max = ReglesClient.NOM_MAX) String nom,
            @Size(max = ReglesClient.TELEPHONE_MAX) String telephone,
            @Size(max = ReglesClient.NOTE_MAX) String note,

            @Schema(nullable = true, description = "Plafond de crédit, sans limite si absent")
            @DecimalMin("0.01")
            @Digits(integer = 10, fraction = 2)
            BigDecimal plafond,

            @Schema(nullable = true, description = "Actif si absent")
            Boolean actif,

            @Schema(nullable = true, description = "Identifiant commun de l'entreprise, 15 chiffres") @Size(max = 20)
            String ice,

            @Schema(nullable = true) @Size(max = ReglesClient.IDENTIFIANT_FISCAL_MAX)
            String identifiantFiscal,

            @Schema(nullable = true) @Size(max = ReglesClient.RC_MAX)
            String rc,

            @Schema(nullable = true) @Size(max = ReglesClient.CONTACT_MAX)
            String contact,

            @Schema(nullable = true) @Size(max = ReglesClient.ADRESSE_MAX)
            String adresse,

            @Schema(nullable = true) @Size(max = ReglesClient.EMAIL_MAX)
            String email) {

        public FicheClient fiche() {
            return ReglesClient.fiche(
                    type, nom, telephone, note, plafond, ice, identifiantFiscal, rc, contact, adresse, email);
        }
    }

    /** @param supprime faux : le client a un historique, il a seulement été désactivé */
    public record SuppressionDto(boolean supprime) {}

    /**
     * Remboursement d'une dette : parts en espèces et par TPE (au moins une non nulle).
     *
     * @param cle clé d'idempotence générée par l'écran : un renvoi ne rembourse pas deux fois
     */
    public record RemboursementCommande(
            @Schema(nullable = true) @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
            BigDecimal especes,

            @Schema(nullable = true) @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
            BigDecimal tpe,

            @Schema(nullable = true) UUID cle) {}

    public record AnnulationCommande(
            @NotBlank @Size(max = ReglesClient.MOTIF_MAX) String motif) {}
}
