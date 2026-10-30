import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Schemas } from "../api/client";
import { ImportCatalogue } from "./ImportCatalogue";

const inventaires = [
  { numero: "INV-2", date: "2026-10-20T08:00:00Z", nombreArticles: 120 },
  { numero: "INV-1", date: "2026-09-01T08:00:00Z", nombreArticles: 98 },
];

function rapport(
  numero: string,
  compteurs: Partial<Schemas["RapportImport"]> = {},
): Schemas["RapportImport"] {
  return {
    format: "INVENTO",
    inventaires,
    inventaireRetenu: inventaires.find((i) => i.numero === numero) ?? null,
    lignesLues: numero === "INV-2" ? 120 : 98,
    produitsCrees: 0,
    produitsMisAJour: 0,
    mouvementsStock: 0,
    produitsEnStockNegatif: 0,
    produitsSansCodeBarre: 0,
    nombreErreurs: 1,
    erreurs: ["Ligne 7 : prix invalide"],
    nombreAvertissements: 0,
    avertissements: [],
    ...compteurs,
  };
}

afterEach(() => vi.unstubAllGlobals());

describe("ImportCatalogue", () => {
  it("analyse le fichier, change d'inventaire puis importe celui retenu", async () => {
    const requetes: Request[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (requete: Request) => {
        requetes.push(requete);
        const url = new URL(requete.url);
        const numero = url.searchParams.get("inventaire") ?? "INV-2";
        const corps = url.pathname.endsWith("/analyse")
          ? rapport(numero)
          : rapport(numero, { produitsCrees: 90, produitsMisAJour: 7, mouvementsStock: 97 });
        return new Response(JSON.stringify(corps), { headers: { "Content-Type": "application/json" } });
      }),
    );
    const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <ImportCatalogue />
      </QueryClientProvider>,
    );

    const fichier = new File(["xlsx"], "invento.xlsx");
    await userEvent.upload(screen.getByLabelText("Fichier"), fichier);
    expect(await screen.findByText(/Export Invento/)).toBeInTheDocument();
    expect(screen.getByText(/1 lignes en erreur seront ignorées/)).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText("Inventaire à importer"), "INV-1");
    await waitFor(() => expect(screen.getByLabelText("Inventaire à importer")).toHaveValue("INV-1"));
    await userEvent.click(screen.getByRole("button", { name: "Importer" }));

    expect(await screen.findByRole("status")).toHaveTextContent("90 produits créés, 7 mis à jour");
    const envoi = requetes.at(-1);
    expect(new URL(envoi?.url ?? "").pathname).toBe("/api/v1/imports/catalogue");
    expect(new URL(envoi?.url ?? "").searchParams.get("inventaire")).toBe("INV-1");
    const formulaire = await envoi?.formData();
    // Sous jsdom, le nom du fichier se perd en route (le navigateur le transmet) : on vérifie le contenu.
    expect((formulaire?.get("fichier") as Blob | null)?.size).toBe(fichier.size);
  });
});
