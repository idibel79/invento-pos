import { useEffect, useState } from "react";

/** Valeur stabilisée après {@code delai} ms sans changement (recherche tapée au clavier). */
export function useValeurDifferee<T>(valeur: T, delai: number): T {
  const [differee, setDifferee] = useState(valeur);
  useEffect(() => {
    const minuterie = setTimeout(() => setDifferee(valeur), delai);
    return () => clearTimeout(minuterie);
  }, [valeur, delai]);
  return differee;
}

/** Taille (largeur ou hauteur) d'un panneau réglée par l'utilisateur, retenue dans ce navigateur (confort d'affichage seulement). */
export function useTailleRetenue(cle: string, defaut: number): [number, (taille: number) => void] {
  const [taille, setTaille] = useState(() => {
    try {
      const retenue = Number(localStorage.getItem(cle));
      return retenue > 0 ? retenue : defaut;
    } catch {
      return defaut;
    }
  });
  useEffect(() => {
    try {
      localStorage.setItem(cle, String(taille));
    } catch {
      // Stockage indisponible (navigation privée) : la taille n'est simplement pas retenue.
    }
  }, [cle, taille]);
  return [taille, setTaille];
}

/** Heure courante, mise à jour à intervalle régulier (comptes à rebours, horloge). */
export function useMaintenant(intervalle = 1000): number {
  const [maintenant, setMaintenant] = useState(() => Date.now());
  useEffect(() => {
    const minuterie = setInterval(() => setMaintenant(Date.now()), intervalle);
    return () => clearInterval(minuterie);
  }, [intervalle]);
  return maintenant;
}
