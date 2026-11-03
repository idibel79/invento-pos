/** Teinte dominante de chaque billet marocain en circulation (série Bank Al-Maghrib 2012). */
const TEINTES: Record<number, { fond: string; motif: string }> = {
  20: { fond: "#6d4a9c", motif: "#a98bd1" },
  50: { fond: "#2f7a4b", motif: "#7fc39a" },
  100: { fond: "#94622f", motif: "#d6a873" },
  200: { fond: "#2b5c9e", motif: "#86aee0" },
};

/** Petite vignette du billet en dirhams, reconnaissable à sa couleur. */
export function BilletDirham({ valeur }: { valeur: number }) {
  const teinte = TEINTES[valeur] ?? { fond: "#4b5563", motif: "#9ca3af" };
  return (
    <svg
      className="billet-image"
      width="28"
      height="16"
      viewBox="0 0 28 16"
      aria-hidden="true"
      focusable="false"
    >
      <rect width="28" height="16" rx="2" fill={teinte.fond} />
      <circle cx="8" cy="8" r="4" fill={teinte.motif} />
    </svg>
  );
}
