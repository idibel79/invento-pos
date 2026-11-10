import { lireMontant } from "../ui/format";

/** Tiroir compté à la clôture : espèces et tickets TPE séparément. */
export interface Comptage {
  especes: number;
  tpe: number;
  total: number;
}

/**
 * Lit les deux saisies du comptage. Un champ vide vaut 0 ; null tant que les deux sont vides ou
 * qu'une saisie est invalide.
 */
export function lireComptage(saisieEspeces: string, saisieTpe: string): Comptage | null {
  const vide = (saisie: string) => saisie.trim() === "";
  if (vide(saisieEspeces) && vide(saisieTpe)) return null;
  const especes = vide(saisieEspeces) ? 0 : lireMontant(saisieEspeces);
  const tpe = vide(saisieTpe) ? 0 : lireMontant(saisieTpe);
  if (especes === null || tpe === null) return null;
  return { especes, tpe, total: arrondi(especes + tpe) };
}

/** Écart par mode (compté − attendu) ; null tant que rien n'est compté. Le serveur recalcule et fait foi. */
export function ecartsParMode(
  attenduEspeces: number,
  attenduTpe: number,
  comptage: Comptage | null,
): { especes: number; tpe: number } | null {
  if (comptage === null) return null;
  return { especes: arrondi(comptage.especes - attenduEspeces), tpe: arrondi(comptage.tpe - attenduTpe) };
}

/** Évite les « −0,00 » et les restes binaires des soustractions de décimaux. */
export function arrondi(valeur: number): number {
  return Math.round(valeur * 100) / 100 || 0;
}

export interface ApercuCloture {
  /** Compté − attendu ; null tant que le compté n'est pas saisi. */
  ecart: number | null;
  /** Solde dû après la clôture : solde avant + excédent − versé (un manque ne réduit pas le solde). */
  resteDu: number;
  /** Ce qui reste avec le caissier une fois le tiroir vidé. */
  resteAvecVous: number;
  /** Versé valide : entre 0 et le compté. */
  verseValide: boolean;
}

/**
 * Aperçu de la clôture affiché pendant la saisie ; le serveur recalcule et fait foi.
 * Le versement ne peut pas dépasser le compté (le tiroir est vidé).
 */
export function apercuCloture(
  attendu: number,
  soldeAvant: number,
  compte: number | null,
  verse: number | null,
): ApercuCloture {
  const ecart = compte === null ? null : arrondi(compte - attendu);
  const verseOuZero = verse ?? 0;
  return {
    ecart,
    resteDu: arrondi(soldeAvant + Math.max(ecart ?? 0, 0) - verseOuZero),
    resteAvecVous: arrondi((compte ?? 0) - verseOuZero),
    verseValide: compte !== null && verse !== null && verse >= 0 && verse <= compte,
  };
}
