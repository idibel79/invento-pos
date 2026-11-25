-- Verrouillage temporaire de la connexion : après 5 mots de passe ratés, le compte est bloqué
-- 15 minutes puis se débloque tout seul (l'admin peut le débloquer avant). La fin du blocage est en
-- base pour que toutes les instances de l'API la voient.
alter table utilisateur add column verrouille_jusqua timestamptz;

-- Les identifiants inconnus suivent la même règle, pour ne pas révéler quels comptes existent.
alter table essai_identifiant_inconnu add column verrouille_jusqua timestamptz;

-- Les comptes déjà verrouillés par la connexion le restent 15 minutes à partir de la migration.
update utilisateur set verrouille_jusqua = now() + interval '15 minutes' where essais_rates >= 5;
update essai_identifiant_inconnu set verrouille_jusqua = now() + interval '15 minutes' where essais >= 5;
