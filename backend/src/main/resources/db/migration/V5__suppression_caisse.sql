-- Suppression logique d'une caisse inactive : elle disparaît des écrans mais garde son historique
-- (sessions, ventes, journal). Son nom redevient disponible pour une nouvelle caisse.
alter table caisse add column supprimee boolean not null default false;
alter table caisse drop constraint uk_caisse_nom;
create unique index uk_caisse_nom on caisse (lower(nom)) where not supprimee;
