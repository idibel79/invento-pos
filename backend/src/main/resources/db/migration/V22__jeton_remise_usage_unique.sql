-- Jetons d'autorisation de remise (PIN) déjà utilisés : un jeton sert à une seule vente. Il est inscrit
-- ici dans la même transaction que la vente ; la clé primaire refuse une deuxième utilisation, même
-- depuis une autre instance de l'API. Une ligne ne sert plus après l'échéance du jeton : elle est purgée.
create table jeton_remise_utilise (
    id uuid primary key,
    expire_le timestamptz not null
);

create index jeton_remise_utilise_expire_le on jeton_remise_utilise (expire_le);
