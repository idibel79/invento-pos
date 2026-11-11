package ma.invento.pos.client.domain;

import java.math.BigDecimal;

/**
 * Fiche d'un client nettoyée et validée. Pour une entreprise, {@code nom} est la raison sociale ; les
 * champs propres à l'entreprise sont toujours nuls pour un particulier.
 */
public record FicheClient(
        TypeClient type,
        String nom,
        String telephone,
        String note,
        BigDecimal plafond,
        String ice,
        String identifiantFiscal,
        String rc,
        String contact,
        String adresse,
        String email) {}
