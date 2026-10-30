-- Modèle de données initial du POS (cadrage § 6), PostgreSQL.
-- Les identifiants viennent de séquences incrémentées par 50 pour permettre le batch JDBC
-- (allocationSize = 50 côté Hibernate).

-- ---------------------------------------------------------------- Référentiels du catalogue

create sequence categorie_seq increment by 50;
create table categorie (
    id      bigint primary key,
    nom     varchar(120) not null,
    constraint uk_categorie_nom unique (nom)
);

create sequence zone_seq increment by 50;
create table zone (
    id      bigint primary key,
    nom     varchar(120) not null,
    constraint uk_zone_nom unique (nom)
);

create sequence tva_seq increment by 50;
create table tva (
    id      bigint primary key,
    libelle varchar(60)   not null,
    taux    numeric(5, 2) not null,
    constraint uk_tva_taux unique (taux),
    constraint ck_tva_taux check (taux >= 0 and taux <= 100)
);

-- ---------------------------------------------------------------- Produits

create sequence produit_seq increment by 50;
create table produit (
    id              bigint primary key,
    pid             varchar(40)    not null,
    designation     varchar(255)   not null,
    categorie_id    bigint         not null references categorie (id),
    tva_id          bigint         not null references tva (id),
    zone_id         bigint         not null references zone (id),
    ppv             numeric(12, 2) not null,
    pph             numeric(12, 2),
    -- Taux de marge de la catégorie Sobrus au moment de l'import (information, pas de calcul).
    taux_marge      numeric(6, 3),
    -- Peut être négatif à l'import (données Sobrus) : la vente est alors bloquée.
    stock           integer        not null default 0,
    -- Quantités bloquées par les ventes mises en attente (5 min max).
    stock_reserve   integer        not null default 0,
    stock_min       integer,
    stock_max       integer,
    date_peremption date,
    actif           boolean        not null default true,
    version         bigint         not null default 0,
    cree_le         timestamptz    not null,
    modifie_le      timestamptz    not null,
    constraint uk_produit_pid unique (pid),
    constraint ck_produit_prix check (ppv >= 0 and (pph is null or pph >= 0)),
    constraint ck_produit_reserve check (stock_reserve >= 0)
);
create index ix_produit_categorie on produit (categorie_id);
create index ix_produit_zone on produit (zone_id);
create index ix_produit_designation on produit (lower(designation));

create sequence code_barre_seq increment by 50;
create table code_barre (
    id         bigint primary key,
    produit_id bigint      not null references produit (id) on delete cascade,
    code       varchar(64) not null,
    constraint uk_code_barre_code unique (code)
);
create index ix_code_barre_produit on code_barre (produit_id);

-- ---------------------------------------------------------------- Utilisateurs et caisses

create sequence caisse_seq increment by 50;
create table caisse (
    id     bigint primary key,
    nom    varchar(80) not null,
    active boolean     not null default true,
    constraint uk_caisse_nom unique (nom)
);

create sequence utilisateur_seq increment by 50;
create table utilisateur (
    id                bigint primary key,
    nom               varchar(80)  not null,
    prenom            varchar(80)  not null,
    login             varchar(60)  not null,
    mot_de_passe_hash varchar(100) not null,
    pin_hash          varchar(100),
    role              varchar(20)  not null,
    actif             boolean      not null default true,
    doit_changer_mdp  boolean      not null default true,
    essais_rates      integer      not null default 0,
    essais_pin_rates  integer      not null default 0,
    derniere_connexion timestamptz,
    version           bigint       not null default 0,
    cree_le           timestamptz  not null,
    constraint uk_utilisateur_login unique (login),
    constraint ck_utilisateur_role check (role in ('CAISSIER', 'RESPONSABLE', 'ADMIN'))
);
-- Un seul admin (cadrage § 1).
create unique index uk_utilisateur_admin_unique on utilisateur (role) where role = 'ADMIN';

create table utilisateur_caisse (
    utilisateur_id bigint not null references utilisateur (id),
    caisse_id      bigint not null references caisse (id),
    primary key (utilisateur_id, caisse_id)
);

create sequence refresh_token_seq increment by 50;
create table refresh_token (
    id             bigint primary key,
    utilisateur_id bigint      not null references utilisateur (id),
    token_hash     varchar(64) not null,
    expire_le      timestamptz not null,
    revoque        boolean     not null default false,
    cree_le        timestamptz not null,
    constraint uk_refresh_token_hash unique (token_hash)
);
create index ix_refresh_token_utilisateur on refresh_token (utilisateur_id);

-- Code de secours de l'admin : affiché une fois, stocké haché, à usage unique.
create sequence code_secours_seq increment by 50;
create table code_secours (
    id        bigint primary key,
    code_hash varchar(100) not null,
    cree_le   timestamptz  not null,
    utilise_le timestamptz
);

create sequence session_caisse_seq increment by 50;
create table session_caisse (
    id             bigint primary key,
    caisse_id      bigint         not null references caisse (id),
    caissier_id    bigint         not null references utilisateur (id),
    ouverture      timestamptz    not null,
    fermeture      timestamptz,
    fond_initial   numeric(12, 2) not null,
    montant_compte numeric(12, 2),
    ecart          numeric(12, 2)
);
-- Une seule session ouverte par caisse.
create unique index uk_session_caisse_ouverte on session_caisse (caisse_id) where fermeture is null;

-- ---------------------------------------------------------------- Paramètres et journal

create table parametre (
    cle    varchar(60)  primary key,
    valeur varchar(500) not null
);
insert into parametre (cle, valeur) values
    ('plafond_remise_caissier', '10'),
    ('plafond_remise_responsable', '30'),
    ('boutique_nom', ''),
    ('boutique_adresse', ''),
    ('fuseau_horaire', 'Africa/Casablanca');

create sequence journal_action_seq increment by 50;
create table journal_action (
    id             bigint primary key,
    utilisateur_id bigint references utilisateur (id),
    action         varchar(60)   not null,
    detail         varchar(1000),
    cree_le        timestamptz   not null
);
create index ix_journal_action_date on journal_action (cree_le);

-- ---------------------------------------------------------------- Ventes (utilisées à l'étape 2)

create sequence promotion_seq increment by 50;
create table promotion (
    id       bigint primary key,
    nom      varchar(120)   not null,
    type     varchar(10)    not null,
    valeur   numeric(12, 2) not null,
    cible    varchar(10)    not null,
    cible_id bigint         not null,
    debut    timestamptz    not null,
    fin      timestamptz    not null,
    active   boolean        not null default true,
    constraint ck_promotion_type check (type in ('POURCENT', 'MONTANT')),
    constraint ck_promotion_cible check (cible in ('PRODUIT', 'CATEGORIE')),
    constraint ck_promotion_periode check (fin > debut)
);

create sequence vente_seq increment by 50;
create table vente (
    id                    bigint primary key,
    numero                varchar(30)    not null,
    -- Clé fournie par le client pour rendre l'envoi d'une vente idempotent.
    cle_idempotence       varchar(64)    not null,
    caisse_id             bigint         not null references caisse (id),
    caissier_id           bigint         not null references utilisateur (id),
    session_caisse_id     bigint         not null references session_caisse (id),
    statut                varchar(15)    not null,
    total_ht              numeric(12, 2) not null default 0,
    total_tva             numeric(12, 2) not null default 0,
    total_ttc             numeric(12, 2) not null default 0,
    remise_ticket_type    varchar(10),
    remise_ticket_valeur  numeric(12, 2),
    remise_ticket_montant numeric(12, 2),
    motif_remise          varchar(255),
    validee_par           bigint references utilisateur (id),
    -- Mise en attente : note facultative et échéance (5 min max), libération du stock réservé après.
    note_attente          varchar(255),
    attente_expire_le     timestamptz,
    cree_le               timestamptz    not null,
    finalisee_le          timestamptz,
    version               bigint         not null default 0,
    constraint uk_vente_numero unique (numero),
    constraint uk_vente_idempotence unique (cle_idempotence),
    constraint ck_vente_statut check (statut in ('EN_ATTENTE', 'VALIDEE', 'ANNULEE')),
    constraint ck_vente_remise_type check (remise_ticket_type is null or remise_ticket_type in ('POURCENT', 'MONTANT')),
    constraint ck_vente_attente check (statut <> 'EN_ATTENTE' or attente_expire_le is not null)
);
create index ix_vente_session on vente (session_caisse_id);
create index ix_vente_date on vente (cree_le);
create index ix_vente_attente on vente (attente_expire_le) where statut = 'EN_ATTENTE';

create sequence ligne_vente_seq increment by 50;
create table ligne_vente (
    id             bigint primary key,
    vente_id       bigint         not null references vente (id) on delete cascade,
    produit_id     bigint         not null references produit (id),
    quantite       integer        not null,
    prix_unitaire  numeric(12, 2) not null,
    taux_tva       numeric(5, 2)  not null,
    remise_type    varchar(10),
    remise_valeur  numeric(12, 2),
    remise_montant numeric(12, 2) not null default 0,
    promotion_id   bigint references promotion (id),
    constraint ck_ligne_vente_quantite check (quantite > 0),
    constraint ck_ligne_vente_remise_type check (remise_type is null or remise_type in ('POURCENT', 'MONTANT'))
);
create index ix_ligne_vente_vente on ligne_vente (vente_id);
create index ix_ligne_vente_produit on ligne_vente (produit_id);

create sequence paiement_seq increment by 50;
create table paiement (
    id           bigint primary key,
    vente_id     bigint         not null references vente (id) on delete cascade,
    mode         varchar(10)    not null,
    montant      numeric(12, 2) not null,
    montant_recu numeric(12, 2),
    rendu        numeric(12, 2),
    constraint ck_paiement_mode check (mode in ('ESPECES', 'CARTE')),
    constraint ck_paiement_montant check (montant > 0)
);
create index ix_paiement_vente on paiement (vente_id);

-- ---------------------------------------------------------------- Mouvements de stock

create sequence mouvement_stock_seq increment by 50;
create table mouvement_stock (
    id             bigint primary key,
    produit_id     bigint       not null references produit (id),
    type           varchar(12)  not null,
    -- Variation signée du stock (+ entrée, - sortie).
    quantite       integer      not null,
    reference      varchar(120),
    cree_le        timestamptz  not null,
    utilisateur_id bigint references utilisateur (id),
    constraint ck_mouvement_type check (type in ('VENTE', 'RETOUR', 'RECEPTION', 'AJUSTEMENT', 'IMPORT'))
);
create index ix_mouvement_produit on mouvement_stock (produit_id, cree_le);
