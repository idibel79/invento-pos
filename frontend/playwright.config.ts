import { defineConfig, devices } from "@playwright/test";

// Parcours de vente de bout en bout, contre l'application complète (Spring Boot qui sert le
// build React, PostgreSQL). Démarrée à part : en CI par le job e2e, en local avec `run`.
export default defineConfig({
  testDir: "e2e",
  timeout: 60_000,
  fullyParallel: false,
  // Les parcours partagent le compte admin (mot de passe initial remplacé au premier passage).
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: process.env.POS_URL ?? "http://localhost:8080",
    trace: "retain-on-failure",
    locale: "fr-FR",
    viewport: { width: 1440, height: 900 },
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } } },
  ],
});
