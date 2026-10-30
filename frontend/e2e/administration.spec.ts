import { expect, test } from "@playwright/test";
import { jetonAdmin, MDP_ADMIN } from "./admin";

test("l'admin crée une caisse et un caissier, définit son PIN et régénère le code de secours", async ({
  page,
  request,
}) => {
  await jetonAdmin(request);
  const suffixe = Date.now().toString(36);
  const caisse = `Admin E2E ${suffixe}`;
  const login = `vendeur-${suffixe}`;

  await page.goto("/");
  await page.getByLabel("Identifiant").fill("admin");
  await page.getByLabel("Mot de passe", { exact: true }).fill(MDP_ADMIN);
  await page.getByRole("button", { name: "Se connecter" }).click();
  await page.getByRole("button", { name: "Administration" }).click();

  // Caisses
  await page.getByRole("button", { name: "Nouvelle caisse" }).click();
  const fiche = page.getByRole("dialog", { name: "Nouvelle caisse" });
  await fiche.getByLabel("Nom").fill(caisse);
  await fiche.getByRole("button", { name: "Enregistrer" }).click();
  await expect(fiche).toBeHidden();
  const ligneCaisse = page.getByRole("listitem").filter({ hasText: caisse });
  await expect(ligneCaisse).toContainText("Active");

  // Utilisateurs : création avec la caisse attribuée, puis PIN.
  await page.getByRole("button", { name: "Utilisateurs" }).click();
  await page.getByRole("button", { name: "Nouvel utilisateur" }).click();
  const compte = page.getByRole("dialog", { name: "Nouvel utilisateur" });
  await compte.getByLabel("Prénom").fill("Nadia");
  await compte.getByLabel("Nom", { exact: true }).fill("Tazi");
  await compte.getByLabel("Identifiant").fill(login);
  await compte.getByLabel("Mot de passe temporaire").fill("Temporaire-2026");
  await compte.getByLabel(caisse).check();
  await compte.getByRole("button", { name: "Enregistrer" }).click();
  await expect(compte).toBeHidden();
  const ligne = page.getByRole("listitem").filter({ hasText: login });
  await expect(ligne).toContainText(caisse);
  await expect(ligne).toContainText("Mot de passe temporaire");

  await ligne.getByRole("button", { name: "PIN" }).click();
  const pin = page.getByRole("dialog", { name: "PIN de Nadia" });
  await pin.getByLabel("Nouveau PIN").fill("4821");
  await pin.getByLabel("Confirmation").fill("4821");
  await pin.getByRole("button", { name: "Enregistrer" }).click();
  await expect(pin).toBeHidden();
  await expect(ligne.getByText("PIN défini")).toBeVisible();

  // Code de secours : montré une seule fois.
  await page.getByRole("button", { name: "Code de secours" }).click();
  await page.getByRole("button", { name: "Générer un nouveau code" }).click();
  const secours = page.getByRole("dialog", { name: "Remplacer le code de secours ?" });
  await secours.getByRole("button", { name: "Générer" }).click();
  await expect(page.getByTestId("code-secours")).toHaveText(/\S{8,}/);
  await page.getByRole("button", { name: "Je l'ai noté" }).click();

  // La nouvelle caisse peut être ouverte.
  await page.getByRole("button", { name: "Retour à la caisse" }).click();
  await expect(page.getByRole("heading", { name: "Ouvrir la caisse" })).toBeVisible();
  await expect(page.getByLabel("Caisse", { exact: true }).locator("option", { hasText: caisse })).toHaveCount(
    1,
  );
});
