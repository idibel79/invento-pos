import { expect, test, type APIRequestContext } from "@playwright/test";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { jetonAdmin } from "./admin";

const MDP_TEMPORAIRE = "Temporaire-2026";
const MDP_CAISSIER = "Caissier-E2E-2026!";
const FICHIER_SOBRUS = fileURLToPath(
  new URL("../../backend/src/test/resources/import/sobrus-stocks-2026-10-02.xlsx", import.meta.url),
);

interface Produit {
  id: number;
  designation: string;
  ppv: number;
  stockDisponible: number;
  codesBarres: string[];
}

/**
 * Catalogue Sobrus importé, une caisse et un caissier dédiés à ce passage. Le fond de caisse est pris
 * sur le compte du caissier : la caisse principale lui verse 500 DH, qu'il confirme à sa connexion.
 */
async function preparer(api: APIRequestContext) {
  const admin = await jetonAdmin(api);
  const auth = { Authorization: `Bearer ${admin}` };
  const suffixe = Date.now().toString(36);

  let produits = await api.get("/api/v1/produits?disponibilite=EN_STOCK&size=200", { headers: auth });
  if ((await produits.json()).totalElements === 0) {
    const importe = await api.post("/api/v1/imports/catalogue", {
      headers: auth,
      multipart: {
        fichier: {
          name: "sobrus.xlsx",
          mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          buffer: readFileSync(FICHIER_SOBRUS),
        },
      },
    });
    expect(importe.ok()).toBeTruthy();
    produits = await api.get("/api/v1/produits?disponibilite=EN_STOCK&size=200", { headers: auth });
  }
  const vendables = ((await produits.json()).contenu as Produit[]).filter(
    (p) => p.stockDisponible >= 3 && p.codesBarres.length > 0 && p.ppv > 0,
  );
  expect(vendables.length).toBeGreaterThanOrEqual(2);

  const caisse = await api.post("/api/v1/caisses", {
    headers: auth,
    data: { nom: `E2E ${suffixe}`, active: true },
  });
  expect(caisse.ok()).toBeTruthy();
  const login = `caissier-${suffixe}`;
  const caissier = await api.post("/api/v1/utilisateurs", {
    headers: auth,
    data: {
      nom: "Bennani",
      prenom: "Salma",
      login,
      role: "CAISSIER",
      motDePasseTemporaire: MDP_TEMPORAIRE,
      caisseIds: [(await caisse.json()).id],
      seuilsRemise: { ligne: 0, ticket: 0 },
    },
  });
  expect(caissier.ok()).toBeTruthy();
  const alimentation = await api.post("/api/v1/tresorerie/principale/alimentation", {
    headers: auth,
    data: { montant: 500, motif: "Fond de caisse E2E" },
  });
  expect(alimentation.ok()).toBeTruthy();
  const versement = await api.post("/api/v1/tresorerie/principale/versements", {
    headers: auth,
    data: { utilisateurId: (await caissier.json()).id, montant: 500 },
  });
  expect(versement.ok()).toBeTruthy();
  return {
    login,
    versementId: (await versement.json()).id as number,
    scanne: vendables[0] as Produit,
    attente: vendables[1] as Produit,
    caisse: `E2E ${suffixe}`,
  };
}

test("vente en espèces, vente mise en attente puis reprise, clôture de caisse", async ({ page, request }) => {
  const { login, versementId, scanne, attente, caisse } = await preparer(request);

  // Première connexion : mot de passe temporaire à remplacer, puis ouverture de la caisse.
  await page.goto("/");
  await page.getByLabel("Identifiant").fill(login);
  await page.getByLabel("Mot de passe", { exact: true }).fill(MDP_TEMPORAIRE);
  await page.getByRole("button", { name: "Se connecter" }).click();
  await page.getByLabel("Mot de passe actuel").fill(MDP_TEMPORAIRE);
  await page.getByLabel("Nouveau mot de passe").fill(MDP_CAISSIER);
  await page.getByLabel("Confirmation").fill(MDP_CAISSIER);
  await page.getByRole("button", { name: "Enregistrer" }).click();

  await expect(page.getByRole("heading", { name: "Ouvrir la caisse" })).toBeVisible();
  await recevoir(request, login, versementId);
  await page.reload();
  await expect(page.getByText("Disponible : 500,00 DH")).toBeVisible();
  await page.getByLabel("Caisse", { exact: true }).selectOption({ label: caisse });
  await page.getByLabel("Fond de caisse pris sur mon compte (DH)").fill("500");
  await page.getByRole("button", { name: "Ouvrir la caisse" }).click();

  // Scan à la douchette : code-barre puis Entrée.
  const recherche = page.getByPlaceholder("Scanner un code-barre ou rechercher (nom, PID)");
  await recherche.fill(scanne.codesBarres[0] as string);
  await recherche.press("Enter");
  const panier = page.getByRole("complementary", { name: "Vente en cours" });
  await expect(panier.getByText(scanne.designation)).toBeVisible();
  await panier.getByRole("button", { name: `Augmenter ${scanne.designation}` }).click();
  await expect(panier.getByLabel("Quantité")).toHaveText("2");

  const encaisser = panier.getByRole("button", { name: /^Encaisser / });
  await expect(encaisser).toBeEnabled();
  await encaisser.click();
  const encaissement = page.getByRole("dialog", { name: "Encaissement" });
  // Billet arrondi au-dessus du total : la monnaie à rendre reste dans ce que contient le tiroir (fond de 500 DH).
  const total = Math.round(scanne.ppv * 2 * 100) / 100;
  const recu = (Math.floor(total / 100) + 1) * 100;
  await encaissement.getByLabel("Montant reçu").fill(String(recu));
  await expect(encaissement.getByText("Rendu monnaie")).toBeVisible();
  await encaissement.getByRole("button", { name: /^Valider · rendre/ }).click();

  const validee = page.getByRole("dialog", { name: /Vente C\d+-\d+ validée/ });
  await expect(validee).toBeVisible();
  const rendu = Math.round((recu - total) * 100) / 100;
  await expect(validee.getByTestId("rendu")).toHaveText(
    `${new Intl.NumberFormat("fr-FR", { minimumFractionDigits: 2 }).format(rendu)} DH`,
  );
  await validee.getByRole("button", { name: "Nouvelle vente" }).click();

  // Le client part chercher de quoi payer : vente en attente, puis reprise sur la même caisse.
  await recherche.fill(attente.designation);
  await page
    .getByRole("button", { name: new RegExp(escape(attente.designation)) })
    .first()
    .click();
  await expect(panier.getByText(attente.designation)).toBeVisible();
  await panier.getByRole("button", { name: "Mettre en attente" }).click();
  const miseEnAttente = page.getByRole("dialog", { name: "Mettre la vente en attente" });
  await miseEnAttente.getByLabel("Note (facultative)").fill("Parti au distributeur");
  await miseEnAttente.getByRole("button", { name: "Mettre en attente" }).click();
  await expect(page.getByRole("status")).toContainText("mise en attente");
  await expect(panier.getByText("Scannez ou touchez un produit")).toBeVisible();

  await page.getByRole("button", { name: "En attente (1)" }).click();
  const liste = page.getByRole("dialog", { name: "Ventes en attente" });
  await expect(liste.getByText("Parti au distributeur", { exact: false })).toBeVisible();
  await liste.getByRole("button", { name: "Reprendre" }).click();
  await expect(panier.getByText(/Vente reprise C\d+-\d+/)).toBeVisible();
  await expect(panier.getByText(attente.designation)).toBeVisible();

  await panier.getByRole("button", { name: /^Encaisser / }).click();
  await encaissement.getByRole("button", { name: "Montant exact" }).click();
  await encaissement.getByRole("button", { name: "Valider la vente" }).click();
  await expect(page.getByRole("dialog", { name: /validée/ })).toBeVisible();
  await page.getByRole("button", { name: "Nouvelle vente" }).click();
  await expect(page.getByRole("button", { name: "En attente (0)" })).toBeVisible();

  // Clôture : le tiroir contient le fond et les espèces encaissées, tout est versé à la caisse principale.
  const attendu = Math.round((500 + total + attente.ppv) * 100) / 100;
  await page.getByRole("button", { name: "Clôturer" }).click();
  const cloture = page.getByRole("dialog", { name: "Clôturer la caisse" });
  await cloture.getByLabel("1. Compté dans le tiroir").fill(String(attendu));
  await expect(cloture.getByRole("status")).toContainText("0,00");
  await cloture.getByRole("button", { name: "Tout le compté" }).click();
  await cloture.getByRole("button", { name: /^Clôturer et verser/ }).click();
  await expect(page.getByRole("dialog", { name: "Caisse clôturée" })).toBeVisible();
  await page.getByRole("button", { name: "Terminer" }).click();
  await expect(page.getByRole("heading", { name: "Ouvrir la caisse" })).toBeVisible();
});

/** Le caissier confirme avoir reçu le versement de la caisse principale. */
async function recevoir(api: APIRequestContext, login: string, versementId: number) {
  const connexion = await api.post("/api/v1/auth/connexion", { data: { login, motDePasse: MDP_CAISSIER } });
  expect(connexion.ok()).toBeTruthy();
  const reponse = await api.post(`/api/v1/tresorerie/versements/${versementId}/validation`, {
    headers: { Authorization: `Bearer ${(await connexion.json()).accessToken}` },
  });
  expect(reponse.ok()).toBeTruthy();
}

function escape(texte: string): string {
  return texte.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
