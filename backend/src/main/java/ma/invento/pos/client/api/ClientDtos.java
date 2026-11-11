package ma.invento.pos.client.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.invento.pos.client.domain.ReglesClient;
import ma.invento.pos.client.domain.TypeMouvementClient;
import ma.invento.pos.client.infrastructure.ClientsJdbc.Client;
import ma.invento.pos.client.infrastructure.ClientsJdbc.Mouvement;

/** Contrats de l'API des clients. */
public final class ClientDtos {

    private ClientDtos() {}

    public record ClientDto(
            Long id,
            String nom,
            @Schema(nullable = true) String telephone,
            @Schema(nullable = true) String note,
            boolean actif,
            BigDecimal dette,

            @Schema(nullable = true, description = "Plafond de crédit, sans limite si absent")
            BigDecimal plafond,

            @Schema(nullable = true) Instant dernierMouvement) {

        public static ClientDto depuis(Client c) {
            return new ClientDto(
                    c.id(), c.nom(), c.telephone(), c.note(), c.actif(), c.dette(), c.plafond(), c.dernierMouvement());
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

    public record ClientCommande(
            @NotBlank @Size(max = ReglesClient.NOM_MAX) String nom,
            @Size(max = ReglesClient.TELEPHONE_MAX) String telephone,
            @Size(max = ReglesClient.NOTE_MAX) String note,

            @Schema(nullable = true, description = "Plafond de crédit, sans limite si absent")
            @DecimalMin("0.01")
            @Digits(integer = 10, fraction = 2)
            BigDecimal plafond,

            @Schema(nullable = true, description = "Actif si absent")
            Boolean actif) {}

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
