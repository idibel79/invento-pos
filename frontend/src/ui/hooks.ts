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

/** Heure courante, mise à jour à intervalle régulier (comptes à rebours, horloge). */
export function useMaintenant(intervalle = 1000): number {
  const [maintenant, setMaintenant] = useState(() => Date.now());
  useEffect(() => {
    const minuterie = setInterval(() => setMaintenant(Date.now()), intervalle);
    return () => clearInterval(minuterie);
  }, [intervalle]);
  return maintenant;
}
