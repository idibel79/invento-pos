import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { EncaissementDialogue, montantsProposes } from "./EncaissementDialogue";

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
  it("propose des montants ronds au-dessus du total", () => {
    expect(montantsProposes(260.6)).toEqual([270, 300, 400]);
    expect(montantsProposes(50)).toEqual([100, 200]);
  });

  it("affiche le rendu ou le manque", async () => {
    afficher();
    const champ = screen.getByLabelText("Montant reçu");
    await userEvent.type(champ, "250");
    expect(espaces(screen.getByText(/Il manque/).nextSibling?.textContent ?? null)).toBe("10,60 DH");
    expect(screen.getByRole("button", { name: "Valider la vente" })).toBeDisabled();

    await userEvent.click(screen.getByRole("button", { name: "300" }));
    expect(espaces(screen.getByText("Rendu monnaie").nextSibling?.textContent ?? null)).toBe("39,40 DH");
  });

  it("envoie la vente avec une clé d'idempotence, conservée en cas de nouvel essai", async () => {
    const requetes: Request[] = [];
    const fetchSimule = vi.fn(async (requete: Request) => {
      requetes.push(requete);
      if (requetes.length === 1) return new Response(null, { status: 503 });
      return new Response(JSON.stringify({ id: 1, numero: "C1-000001" }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      });
    });
    vi.stubGlobal("fetch", fetchSimule);
    const onValidee = afficher();

    await userEvent.click(screen.getByRole("button", { name: "Exact" }));
    await userEvent.click(screen.getByRole("button", { name: "Valider la vente" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Le serveur a rencontré une erreur");

    await userEvent.click(screen.getByRole("button", { name: "Valider la vente" }));
    await waitFor(() => expect(onValidee).toHaveBeenCalled());

    const [premiere, seconde] = requetes;
    expect(premiere?.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
    expect(seconde?.headers.get("Idempotency-Key")).toBe(premiere?.headers.get("Idempotency-Key"));
    expect(await seconde?.json()).toEqual({
      panier: { lignes: [{ produitId: 7, quantite: 2 }] },
      especesRecues: 260.6,
    });
  });
});
