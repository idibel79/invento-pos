-- Désignation du produit figée sur la ligne au moment de la vente : un ticket réimprimé ou l'historique
-- affichent le libellé vendu, même si le produit est renommé ensuite.
alter table ligne_vente add column designation varchar(255);
update ligne_vente lv set designation = p.designation from produit p where p.id = lv.produit_id;
alter table ligne_vente alter column designation set not null;
