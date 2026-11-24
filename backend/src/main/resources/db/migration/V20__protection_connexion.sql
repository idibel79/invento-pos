-- Essais ratés par adresse IP (empreinte SHA-256, jamais l'adresse en clair), par fenêtre de temps
-- fixe : au-delà d'un plafond, la connexion et le code de secours répondent « réessayez plus tard ».
create table limite_essai (
    cle varchar(100) not null,
    fenetre timestamptz not null,
    echecs integer not null,
    primary key (cle, fenetre)
);

-- Compteur d'essais pour les identifiants inconnus (empreinte de l'identifiant saisi) : ils reçoivent
-- les mêmes réponses qu'un vrai compte (essais restants, puis verrouillage), pour ne pas révéler quels
-- comptes existent.
create table essai_identifiant_inconnu (
    identifiant_hash char(64) primary key,
    essais integer not null,
    mis_a_jour timestamptz not null
);
