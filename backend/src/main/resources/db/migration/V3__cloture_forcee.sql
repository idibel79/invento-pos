-- Session clôturée d'office (caisse désactivée par l'admin) : espèces non comptées, pas d'écart.
alter table session_caisse add column cloture_forcee boolean not null default false;
