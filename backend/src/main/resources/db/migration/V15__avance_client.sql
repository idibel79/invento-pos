-- Un client peut rembourser plus que sa dette : le surplus est une avance (dette négative), déduite de
-- ses prochaines ventes à crédit.
alter table client drop constraint ck_client_dette;
