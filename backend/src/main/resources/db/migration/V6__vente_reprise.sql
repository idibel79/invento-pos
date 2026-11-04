-- Vente en attente reprise dans le ticket : elle ne réserve plus de stock et n'est plus en attente,
-- elle est encaissée ou annulée ensuite (ticket vidé, clôture de la session).
alter table vente drop constraint ck_vente_statut;
alter table vente add constraint ck_vente_statut check (statut in ('EN_ATTENTE', 'REPRISE', 'VALIDEE', 'ANNULEE'));
