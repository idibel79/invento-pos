package ma.invento.pos.facture.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import ma.invento.pos.client.domain.TypeClient;
import ma.invento.pos.vente.api.VenteDtos.VenteDto;

/** Contrats de l'API des factures. */
public final class FactureDtos {

    private FactureDtos() {}

    /**
     * Facture A4 d'une vente avec client. L'en-tête de la boutique vient des paramètres ; le client est
     * figé à l'émission.
     *
     * @param montantEnLettres total TTC en toutes lettres
     */
    public record FactureDto(
            String numero, Instant emiseLe, ClientFactureDto client, VenteDto vente, String montantEnLettres) {}

    /** @param nom nom et prénom, ou raison sociale */
    public record ClientFactureDto(
            TypeClient type,
            String nom,
            @Schema(nullable = true) String ice,
            @Schema(nullable = true) String identifiantFiscal,
            @Schema(nullable = true) String rc,
            @Schema(nullable = true) String contact,
            @Schema(nullable = true) String adresse) {}
}
