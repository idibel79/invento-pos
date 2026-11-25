import { useSyncExternalStore } from "react";

/**
 * Écran affiché, gardé dans la partie « # » de l'adresse (ex. `#/administration/parametres/caisses`)
 * pour que F5 et le bouton Retour du navigateur le retrouvent. Le « # » évite d'avoir à servir
 * `index.html` côté serveur pour chaque chemin.
 */
const CHANGEMENT = "invento:adresse";

function lire(): string {
  return window.location.hash;
}

function abonner(rappel: () => void): () => void {
  window.addEventListener("popstate", rappel);
  window.addEventListener("hashchange", rappel);
  window.addEventListener(CHANGEMENT, rappel);
  return () => {
    window.removeEventListener("popstate", rappel);
    window.removeEventListener("hashchange", rappel);
    window.removeEventListener(CHANGEMENT, rappel);
  };
}

function segmentsDe(hash: string): string[] {
  return hash
    .replace(/^#\/?/, "")
    .split("/")
    .filter((s) => s !== "")
    .map(decodeURIComponent);
}

/** Segments de l'adresse courante, ex. `["administration", "parametres", "caisses"]`. */
export function useAdresse(): string[] {
  const hash = useSyncExternalStore(abonner, lire);
  return segmentsDe(hash);
}

/** Change d'écran ; `remplacer` corrige l'adresse sans ajouter d'étape à l'historique. */
export function naviguer(segments: string[], remplacer = false): void {
  const { pathname, search, hash } = window.location;
  const nouveauHash = segments.length === 0 ? "" : `#/${segments.map(encodeURIComponent).join("/")}`;
  if (nouveauHash === hash) return;
  const cible = pathname + search + nouveauHash;
  if (remplacer) window.history.replaceState(null, "", cible);
  else window.history.pushState(null, "", cible);
  window.dispatchEvent(new Event(CHANGEMENT));
}

/** « Crédits et dettes » → « credits-et-dettes ». */
export function slug(texte: string): string {
  return texte
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}
