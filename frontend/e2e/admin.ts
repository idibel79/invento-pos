import { expect, type APIRequestContext } from "@playwright/test";

// Admin créé au premier démarrage avec POS_ADMIN_INITIAL_PASSWORD (voir la CI).
const MDP_INITIAL_ADMIN = process.env.POS_ADMIN_INITIAL_PASSWORD ?? "Installation-2026";
export const MDP_ADMIN = "Admin-E2E-2026!";

/** Jeton de l'admin, après avoir remplacé son mot de passe initial si besoin. */
export async function jetonAdmin(api: APIRequestContext): Promise<string> {
  let reponse = await api.post("/api/v1/auth/connexion", { data: { login: "admin", motDePasse: MDP_ADMIN } });
  if (reponse.ok()) return (await reponse.json()).accessToken;
  reponse = await api.post("/api/v1/auth/connexion", {
    data: { login: "admin", motDePasse: MDP_INITIAL_ADMIN },
  });
  expect(reponse.ok()).toBeTruthy();
  const temporaire = (await reponse.json()).accessToken;
  reponse = await api.post("/api/v1/auth/mot-de-passe", {
    headers: { Authorization: `Bearer ${temporaire}` },
    data: { motDePasseActuel: MDP_INITIAL_ADMIN, nouveauMotDePasse: MDP_ADMIN },
  });
  expect(reponse.ok()).toBeTruthy();
  return (await reponse.json()).accessToken;
}
