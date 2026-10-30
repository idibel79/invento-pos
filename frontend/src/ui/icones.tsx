// Icônes en trait (style Outlined), héritent de la couleur du texte.
const trait = {
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round",
  strokeLinejoin: "round",
} as const;

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
