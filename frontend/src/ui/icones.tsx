// Icônes en trait (style Outlined), héritent de la couleur du texte.
const trait = {
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round",
  strokeLinejoin: "round",
} as const;

export const IconeCorbeille = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" {...trait}>
    <path d="M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3" />
  </svg>
);

export const IconeBillet = () => (
  <svg width="26" height="26" viewBox="0 0 24 24" {...trait}>
    <rect x="2" y="6" width="20" height="12" rx="2" />
    <circle cx="12" cy="12" r="2.5" />
    <path d="M6 10v4M18 10v4" />
  </svg>
);

export const IconeRemise = () => (
  <svg width="26" height="26" viewBox="0 0 24 24" {...trait}>
    <path d="M19 5L5 19" />
    <circle cx="7" cy="7" r="2.5" />
    <circle cx="17" cy="17" r="2.5" />
  </svg>
);

export const IconeHorloge = () => (
  <svg width="26" height="26" viewBox="0 0 24 24" {...trait}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3 2" />
  </svg>
);

export const IconeCaisse = () => (
  <svg width="26" height="26" viewBox="0 0 24 24" {...trait}>
    <rect x="3" y="10" width="18" height="10" rx="2" />
    <path d="M7 10V5h10v5M7 15h4" />
  </svg>
);

export const IconeAlerte = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" {...trait} strokeWidth={2}>
    <path d="M12 3L2 20h20z" />
    <path d="M12 10v4M12 17h.01" />
  </svg>
);

export const IconeScan = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" {...trait} strokeWidth={2}>
    <path d="M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2" />
    <path d="M7 8v8M10 8v8M13 8v8M17 8v8" />
  </svg>
);

export const IconeValide = () => (
  <svg width="26" height="26" viewBox="0 0 24 24" {...trait}>
    <circle cx="12" cy="12" r="9" />
    <path d="M8 12.5l2.5 2.5L16 9.5" />
  </svg>
);

export const IconeUtilisateur = () => (
  <svg width="26" height="26" viewBox="0 0 24 24" {...trait}>
    <circle cx="12" cy="8" r="4" />
    <path d="M4 20c1.5-4 4.5-6 8-6s6.5 2 8 6" />
  </svg>
);

export const IconeCle = () => (
  <svg width="26" height="26" viewBox="0 0 24 24" {...trait}>
    <circle cx="8" cy="15" r="4" />
    <path d="M11 12l9-9M17 6l3 3M14 9l2 2" />
  </svg>
);

export const IconeImport = () => (
  <svg width="26" height="26" viewBox="0 0 24 24" {...trait}>
    <path d="M12 3v12M7 10l5 5 5-5" />
    <path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
  </svg>
);

export const IconeBouclier = () => (
  <svg width="26" height="26" viewBox="0 0 24 24" {...trait}>
    <path d="M12 3l8 3v6c0 4.5-3.4 8-8 9-4.6-1-8-4.5-8-9V6z" />
    <path d="M9 12l2 2 4-4" />
  </svg>
);

export const IconeOeil = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" {...trait}>
    <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" />
    <circle cx="12" cy="12" r="3" />
  </svg>
);

export const IconeOeilBarre = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" {...trait}>
    <path d="M10.6 5.1A10.4 10.4 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-2.8 3.7M6.6 6.6C3.7 8.5 2 12 2 12s3.5 7 10 7c1.9 0 3.5-.6 4.9-1.4" />
    <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2M3 3l18 18" />
  </svg>
);
