-- Chaque remise sur un article est autorisée séparément : qui l'a accordée est gardé sur la ligne.
-- vente.validee_par reste celui de la remise ticket (ou d'une remise ligne, pour l'historique).
alter table ligne_vente add column remise_validee_par bigint references utilisateur (id);

update ligne_vente lv
set remise_validee_par = v.validee_par
from vente v
where v.id = lv.vente_id and lv.remise_montant > 0 and v.validee_par is not null;
