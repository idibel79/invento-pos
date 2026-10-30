-- Étape 2 : numérotation des tickets, verrous des tâches planifiées (ShedLock).

-- Numéro de ticket : préfixe de la caisse + compteur global (C1-000128).
create sequence vente_numero_seq;

-- Une tâche planifiée ne tourne que sur une instance à la fois (API sur plusieurs instances).
create table shedlock (
    name       varchar(64)  primary key,
    lock_until timestamptz  not null,
    locked_at  timestamptz  not null,
    locked_by  varchar(255) not null
);

-- Session ouverte d'un caissier (une à la fois, toutes caisses confondues).
create unique index uk_session_caissier_ouverte on session_caisse (caissier_id) where fermeture is null;
