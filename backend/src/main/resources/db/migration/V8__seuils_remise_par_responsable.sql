-- Plus de plafonds de remise globaux : l'admin fixe, pour chaque responsable, une remise maximale
-- par ligne et sur le ticket (0 par défaut). Un caissier n'accorde aucune remise et n'a plus de PIN.
alter table utilisateur
    add column remise_max_ligne  numeric(5, 2) not null default 0,
    add column remise_max_ticket numeric(5, 2) not null default 0,
    add constraint ck_utilisateur_remise_max_ligne check (remise_max_ligne between 0 and 100),
    add constraint ck_utilisateur_remise_max_ticket check (remise_max_ticket between 0 and 100);

update utilisateur set pin_hash = null, essais_pin_rates = 0 where role = 'CAISSIER';

delete from parametre where cle in ('plafond_remise_caissier', 'plafond_remise_responsable');
