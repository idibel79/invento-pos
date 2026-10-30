package ma.invento.pos.caisse.application;

/**
 * Publié dans la transaction de clôture, avant le calcul de l'écart : les ventes encore en attente
 * sur la session sont alors annulées et leur stock libéré.
 */
public record SessionCaisseCloturee(long sessionId) {}
