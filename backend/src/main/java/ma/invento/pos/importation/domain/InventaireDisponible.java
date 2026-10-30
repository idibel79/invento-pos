package ma.invento.pos.importation.domain;

import java.time.Instant;

/** Inventaire présent dans un export Invento, proposé au choix avant import. */
public record InventaireDisponible(String numero, Instant date, int nombreArticles) {}
