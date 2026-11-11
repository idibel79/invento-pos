import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { EncaissementDialogue } from "./EncaissementDialogue";

const espaces = (texte: string | null) => (texte ?? "").replace(/[\u202f\u00a0]/g, " ");

function afficher(onValidee = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <EncaissementDialogue
        commande={{ lignes: [{ produitId: 7, quantite: 2 }] }}
        total={260.6}
        onValidee={onValidee}
        onFermer={vi.fn()}
      />
    </QueryClientProvider>,
  );
  return onValidee;
}

afterEach(() => vi.unstubAllGlobals());

describe("EncaissementDialogue", () => {
  it("un billet remplace le montant reçu, sans cumul", async () => {
    afficher();
    await userEvent.click(screen.getByRole("button", { name: "Billet de 200 DH" }));
    await userEvent.click(screen.getByRole("button", { name: "Billet de 200 DH" }));
    expect(screen.getByLabelText("Montant reçu")).toHaveValue("200");
    await userEvent.click(screen.getByRole("button", { name: "Billet de 50 DH" }));
    expect(screen.getByLabelText("Montant reçu")).toHaveValue("50");
  });

  it("affiche le rendu ou le manque", async () => {
    afficher();
    const champ = screen.getByLabelText("Montant reçu");
    await userEvent.type(champ, "250");
    expect(espaces(screen.getByText(/Il manque/).nextSibling?.textContent ?? null)).toBe("10,60 DH");
    expect(screen.getByRole("button", { name: "Valider la vente" })).toBeDisabled();

    await userEvent.clear(champ);
    await userEvent.type(champ, "300");
    expect(espaces(screen.getByText("Rendu monnaie").nextSibling?.textContent ?? null)).toBe("39,40 DH");
  });

  it("envoie la vente avec une clé d'idempotence, conservée en cas de nouvel essai", async () => {
    const requetes: Request[] = [];
    const fetchSimule = vi.fn(async (requete: Request) => {
      // Session courante (monnaie disponible) : sans session connue, pas de contrôle côté écran.
      if (requete.method === "GET") return new Response(null, { status: 204 });
      requetes.push(requete);
      if (requetes.length === 1) return new Response(null, { status: 503 });
      return new Response(JSON.stringify({ id: 1, numero: "C1-000001" }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      });
    });
    vi.stubGlobal("fetch", fetchSimule);
    const onValidee = afficher();

    await userEvent.click(screen.getByRole("button", { name: "Montant exact" }));
    await userEvent.click(screen.getByRole("button", { name: "Valider la vente" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Le serveur a rencontré une erreur");

    await userEvent.click(screen.getByRole("button", { name: "Valider la vente" }));
    await waitFor(() => expect(onValidee).toHaveBeenCalled());

    const [premiere, seconde] = requetes;
    expect(premiere?.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
    expect(seconde?.headers.get("Idempotency-Key")).toBe(premiere?.headers.get("Idempotency-Key"));
    expect(await seconde?.json()).toEqual({
      panier: { lignes: [{ produitId: 7, quantite: 2 }] },
      mode: "ESPECES",
      especesRecues: 260.6,
    });
  });

  it("saisit le montant au pavé numérique", async () => {
    afficher();
    for (const touche of ["3", "0", "0", "Virgule", "5"]) {
      await userEvent.click(screen.getByRole("button", { name: touche }));
    }
    expect(screen.getByLabelText("Montant reçu")).toHaveValue("300,5");
    await userEvent.click(screen.getByRole("button", { name: "Effacer le dernier chiffre" }));
    expect(screen.getByLabelText("Montant reçu")).toHaveValue("300,");
  });

  it("valide un paiement par carte sans montant reçu", async () => {
    const corps: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (requete: Request) => {
        corps.push(await requete.json());
        return new Response(JSON.stringify({ id: 1, numero: "C1-000001" }), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        });
      }),
    );
    const onValidee = afficher();

    await userEvent.click(screen.getByRole("button", { name: "Carte bancaire" }));
    expect(screen.queryByLabelText("Montant reçu")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Valider le paiement par carte" }));
    // Rien n'est envoyé tant que le terminal n'a pas accepté ; « Non » revient à l'encaissement.
    await userEvent.click(screen.getByRole("button", { name: "Non, revenir" }));
    expect(corps).toEqual([]);
    await userEvent.click(screen.getByRole("button", { name: "Valider le paiement par carte" }));
    await userEvent.click(screen.getByRole("button", { name: "Oui, valider la vente" }));
    await waitFor(() => expect(onValidee).toHaveBeenCalled());
    expect(corps).toEqual([{ panier: { lignes: [{ produitId: 7, quantite: 2 }] }, mode: "CARTE" }]);
  });

  it("paie une part en espèces et le reste par carte", async () => {
    const corps: unknown[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (requete: Request) => {
        corps.push(await requete.json());
        return new Response(JSON.stringify({ id: 1, numero: "C1-000001" }), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        });
      }),
    );
    const onValidee = afficher();

    await userEvent.click(screen.getByRole("button", { name: "Espèces + carte" }));
    await userEvent.type(screen.getByLabelText("Payé en espèces"), "100");
    await userEvent.click(screen.getByRole("button", { name: /Valider · 160,60/ }));
    await userEvent.click(screen.getByRole("button", { name: "Oui, valider la vente" }));
    await waitFor(() => expect(onValidee).toHaveBeenCalled());
    expect(corps).toEqual([
      { panier: { lignes: [{ produitId: 7, quantite: 2 }] }, mode: "CARTE", especesRecues: 100 },
    ]);
  });
});
