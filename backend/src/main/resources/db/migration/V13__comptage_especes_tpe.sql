-- Clôture : le tiroir est compté séparément en espèces et en tickets TPE, avec un écart pour chacun.
-- montant_compte et ecart restent les totaux ; les sessions clôturées avant gardent ces colonnes vides.
alter table session_caisse
    add column montant_compte_especes numeric(12, 2),
    add column montant_compte_tpe numeric(12, 2),
    add column ecart_especes numeric(12, 2),
    add column ecart_tpe numeric(12, 2);
