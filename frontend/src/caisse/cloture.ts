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
