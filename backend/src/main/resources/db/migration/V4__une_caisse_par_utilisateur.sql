-- Une seule caisse par utilisateur, et une caisse attribuée à un seul utilisateur.
-- L'admin ouvre toutes les caisses : ses attributions ne servent à rien.
delete from utilisateur_caisse uc
using utilisateur u
where u.id = uc.utilisateur_id and u.role = 'ADMIN';

-- Attributions multiples antérieures : on garde la première caisse de chaque utilisateur,
-- puis le premier utilisateur de chaque caisse.
delete from utilisateur_caisse uc
where exists (select 1 from utilisateur_caisse a
              where a.utilisateur_id = uc.utilisateur_id and a.caisse_id < uc.caisse_id);
delete from utilisateur_caisse uc
where exists (select 1 from utilisateur_caisse a
              where a.caisse_id = uc.caisse_id and a.utilisateur_id < uc.utilisateur_id);

create unique index uk_utilisateur_caisse_utilisateur on utilisateur_caisse (utilisateur_id);
create unique index uk_utilisateur_caisse_caisse on utilisateur_caisse (caisse_id);
