-- Plafond de crédit facultatif : une vente à crédit ne peut pas porter la dette du client au-delà.
alter table client add column plafond numeric(12, 2);
alter table client add constraint ck_client_plafond check (plafond is null or plafond > 0);
