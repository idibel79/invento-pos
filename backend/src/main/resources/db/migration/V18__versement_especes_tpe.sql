-- Versement à la caisse principale : réparti entre espèces et tickets TPE, comme le paiement d'un ticket.
-- montant reste le total ; les versements antérieurs et ceux de la caisse principale gardent ces colonnes vides.
alter table versement
    add column montant_especes numeric(12, 2),
    add column montant_tpe numeric(12, 2),
    add constraint ck_versement_repartition check (
        (montant_especes is null and montant_tpe is null)
        or (montant_especes >= 0 and montant_tpe >= 0 and montant_especes + montant_tpe = montant));
