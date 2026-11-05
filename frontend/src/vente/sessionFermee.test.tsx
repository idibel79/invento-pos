import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useAvisSessionFermee, useSessionCourante } from "../caisse/requetes";
import { useValiderVente } from "./requetes";

const session: Schemas["SessionCaisseDto"] = {
  id: 7,
  caisseId: 1,
  caisseNom: "Caisse 1",
  caissierId: 5,
  ouverture: "2026-11-04T08:00:00Z",
  fermeture: null,
  fondInitial: 200,
  montantCompte: null,
  especesEncaissees: 0,
  ecart: null,
  clotureForcee: false,
  comptageEnAttente: false,
  tiroir: null,
};

const desactivee = "La caisse Caisse 1 a été désactivée : la session est clôturée.";

/** Serveur simulé : la caisse est désactivée après le premier chargement de la session. */
function simulerServeur(refus: { detail: string; code?: string }) {
  let sessionOuverte = true;
  vi.stubGlobal(
    "fetch",
    vi.fn(async (requete: Request) => {
      const chemin = new URL(requete.url).pathname;
      if (chemin === "/api/v1/sessions/courante") {
        return sessionOuverte
          ? new Response(JSON.stringify(session), {
              status: 200,
              headers: { "Content-Type": "application/json" },
            })
          : new Response(null, { status: 204 });
      }
      if (refus.code) sessionOuverte = false;
      return new Response(JSON.stringify({ status: 422, ...refus }), {
        status: 422,
        headers: { "Content-Type": "application/problem+json" },
      });
    }),
  );
}

function Harnais() {
  const courante = useSessionCourante();
  const avis = useAvisSessionFermee();
  const valider = useValiderVente();
  if (courante.isPending) return <p>Chargement</p>;
  return (
    <>
      <p>{courante.data ? `Session ${courante.data.caisseNom}` : "Ouverture de la caisse"}</p>
      {avis && <p role="alert">{avis}</p>}
      {valider.isError && <p>Erreur : {messageErreur(valider.error)}</p>}
      <button
        type="button"
        onClick={() => valider.mutate({ cle: "c1", corps: { panier: { lignes: [] }, mode: "CARTE" } })}
      >
        Encaisser
      </button>
    </>
  );
}

function afficher() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <Harnais />
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("session clôturée d'office", () => {
  it("revient à l'ouverture de la caisse avec le message du serveur", async () => {
    simulerServeur({ detail: desactivee, code: "SESSION_FERMEE" });
    afficher();

    expect(await screen.findByText("Session Caisse 1")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Encaisser" }));

    expect(await screen.findByText("Ouverture de la caisse")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(desactivee);
  });

  it("garde la session sur un autre refus", async () => {
    simulerServeur({ detail: "Stock insuffisant pour le produit 3." });
    afficher();

    expect(await screen.findByText("Session Caisse 1")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Encaisser" }));

    expect(await screen.findByText(/Stock insuffisant/)).toBeInTheDocument();
    expect(screen.getByText("Session Caisse 1")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
