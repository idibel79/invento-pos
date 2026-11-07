-- Historique des ventes (administration) : ventes validées d'un jour, de la plus récente à la plus ancienne.
create index if not exists ix_vente_finalisee on vente (finalisee_le) where statut = 'VALIDEE';
