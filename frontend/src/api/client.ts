import createClient from "openapi-fetch";
import type { components, paths } from "./schema";

/** Schémas du contrat OpenAPI (client généré, jamais écrit à la main). */
export type Schemas = components["schemas"];

// Jeton d'accès en mémoire uniquement (jamais dans le stockage du navigateur) ; le jeton de
// rafraîchissement vit dans un cookie HttpOnly que le JavaScript ne voit pas.
let jetonAcces: string | null = null;
let rafraichissementEnCours: Promise<Schemas["SessionDto"] | null> | null = null;
let surSessionExpiree: () => void = () => {};

export function definirJetonAcces(jeton: string | null): void {
  jetonAcces = jeton;
}

export function quandSessionExpire(rappel: () => void): void {
  surSessionExpiree = rappel;
}

function avecJeton(requete: Request): Request {
  const copie = new Request(requete);
  if (jetonAcces) copie.headers.set("Authorization", `Bearer ${jetonAcces}`);
  return copie;
}

/** Échange le cookie de rafraîchissement contre un nouveau jeton ; un seul échange à la fois. */
export function rafraichirSession(): Promise<Schemas["SessionDto"] | null> {
  rafraichissementEnCours ??= fetch("/api/v1/auth/rafraichir", { method: "POST", credentials: "same-origin" })
    .then(async (reponse) => {
      if (!reponse.ok) return null;
      const session = (await reponse.json()) as Schemas["SessionDto"];
      jetonAcces = session.accessToken;
      return session;
    })
    .catch(() => null)
    .finally(() => {
      rafraichissementEnCours = null;
    });
  return rafraichissementEnCours;
}

/** Jeton expiré (15 min) : un rafraîchissement, puis la requête est rejouée une fois. */
async function fetchAuthentifie(requete: Request): Promise<Response> {
  const copie = requete.clone();
  const reponse = await fetch(avecJeton(requete));
  if (reponse.status !== 401 || new URL(requete.url).pathname.startsWith("/api/v1/auth/")) {
    return reponse;
  }
  if (!(await rafraichirSession())) {
    surSessionExpiree();
    return reponse;
  }
  return fetch(avecJeton(copie));
}

export const api = createClient<paths>({
  baseUrl: globalThis.location?.origin ?? "",
  fetch: fetchAuthentifie,
});
