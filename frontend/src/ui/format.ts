const deuxDecimales = new Intl.NumberFormat("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const jusquADeux = new Intl.NumberFormat("fr-FR", { maximumFractionDigits: 2 });

/** Montant en dirhams : 260,60 DH. */
export function dh(montant: number): string {
  return `${deuxDecimales.format(montant)} DH`;
}

export function decimal(montant: number): string {
  return deuxDecimales.format(montant);
}

export function pourcent(valeur: number): string {
  return `${jusquADeux.format(valeur)} %`;
}

/** Saisie d'un montant (« 300 », « 300,5 », « 300.50 ») ; null si invalide. */
export function lireMontant(saisie: string): number | null {
  const texte = saisie.replace(/[\s\u202f\u00a0]/g, "").replace(",", ".");
  if (!/^\d{1,10}(\.\d{0,2})?$/.test(texte)) return null;
  return Number(texte);
}

/** Heure dans le fuseau de la boutique (paramètre fuseau_horaire). */
export function heure(iso: string, fuseau: string | undefined): string {
  return new Intl.DateTimeFormat("fr-FR", { hour: "2-digit", minute: "2-digit", timeZone: fuseau }).format(
    new Date(iso),
  );
}

/** Date et heure dans le fuseau de la boutique : 30/10/2026 16:29. */
export function dateHeure(iso: string, fuseau: string | undefined): string {
  return new Intl.DateTimeFormat("fr-FR", {
    dateStyle: "short",
    timeStyle: "short",
    timeZone: fuseau,
  }).format(new Date(iso));
}
