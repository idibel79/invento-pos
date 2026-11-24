-- Versement fait ou reçu caisse ouverte : la part qui sort du tiroir (vers la caisse principale) ou qui y
-- entre (reçue de la caisse principale), en espèces et en tickets TPE. Le reste vient du compte du caissier
-- (argent qu'il garde sur lui) ou y va. Hors session, ces parts sont nulles.
alter table versement
    add column tiroir_especes numeric(12, 2) not null default 0,
    add column tiroir_tpe numeric(12, 2) not null default 0,
    add constraint ck_versement_tiroir check (
        tiroir_especes >= 0 and tiroir_tpe >= 0 and tiroir_especes + tiroir_tpe <= montant);

-- Jusqu'ici, tout versement fait ou reçu caisse ouverte passait par le tiroir, en espèces : les clôtures
-- passées gardent le même attendu.
update versement set tiroir_especes = montant
where (session_emission_id is not null and not cloture) or session_reception_id is not null;
