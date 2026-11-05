export interface CaseJour {
  /** AAAA-MM-JJ */
  date: string;
  numero: number;
  dansLeMois: boolean;
}

function iso(annee: number, mois: number, jour: number): string {
  return new Date(Date.UTC(annee, mois - 1, jour)).toISOString().slice(0, 10);
}

/** Grille du mois (AAAA-MM) en semaines complètes commençant le lundi. */
export function grilleMois(mois: string): CaseJour[] {
  const [annee, m] = mois.split("-").map(Number) as [number, number];
  const premier = new Date(Date.UTC(annee, m - 1, 1));
  const decalage = (premier.getUTCDay() + 6) % 7; // lundi = 0
  const jours = new Date(Date.UTC(annee, m, 0)).getUTCDate();
  const total = Math.ceil((decalage + jours) / 7) * 7;
  return Array.from({ length: total }, (_, i) => {
    const date = iso(annee, m, i - decalage + 1);
    return { date, numero: Number(date.slice(8, 10)), dansLeMois: date.slice(0, 7) === mois };
  });
}

/** Mois précédent ou suivant (AAAA-MM). */
export function decalerMois(mois: string, delta: number): string {
  const [annee, m] = mois.split("-").map(Number) as [number, number];
  return iso(annee, m + delta, 1).slice(0, 7);
}

/** « Novembre 2026 ». */
export function libelleMois(mois: string): string {
  const texte = new Intl.DateTimeFormat("fr-FR", { month: "long", year: "numeric", timeZone: "UTC" }).format(
    new Date(`${mois}-01T12:00:00Z`),
  );
  return texte.charAt(0).toUpperCase() + texte.slice(1);
}

/** « Mercredi 4 novembre ». */
export function libelleJour(jour: string): string {
  const texte = new Intl.DateTimeFormat("fr-FR", {
    weekday: "long",
    day: "numeric",
    month: "long",
    timeZone: "UTC",
  }).format(new Date(`${jour}T12:00:00Z`));
  return texte.charAt(0).toUpperCase() + texte.slice(1);
}
