import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Schemas } from "../api/client";
import { Caisses } from "./Caisses";

const suivi: Schemas["SuiviCaisseDto"][] = [
  {
    id: 1,
    nom: "Caisse 1",
    active: true,
    session: {
      caissierId: 7,
      ouverture: "2026-11-04T09:00:00Z",
      fondInitial: 200,
      especesEncaissees: 52.4,
      especesAttendues: 252.4,
    },
  },
  { id: 2, nom: "Caisse 2", active: true, session: null },
  { id: 3, nom: "Ancienne", active: false, session: null },
];

function afficher() {
  const requetes: Request[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (requete: Request) => {
      requetes.push(requete);
      const corps = new URL(requete.url).pathname === "/api/v1/caisses/suivi" ? suivi : [];
      if (requete.method === "DELETE") return new Response(null, { status: 204 });
      return new Response(JSON.stringify(requete.method === "PUT" ? {} : corps), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }),
  );
  render(
    <QueryClientProvider client={new QueryClient()}>
      <Caisses />
    </QueryClientProvider>,
  );
  return requetes;
}

const puts = (requetes: Request[]) => requetes.filter((r) => r.method === "PUT");

afterEach(() => vi.unstubAllGlobals());

describe("Caisses", () => {
  it("affiche le fond, les espèces encaissées et attendues de la session ouverte", async () => {
    afficher();
    const ouverte = (await screen.findByRole("button", { name: "Caisse 1" })).closest("li") as HTMLElement;
    expect(within(ouverte).getByText("200,00 DH")).toBeInTheDocument();
    expect(within(ouverte).getByText("52,40 DH")).toBeInTheDocument();
    expect(within(ouverte).getByText("252,40 DH")).toBeInTheDocument();

    const fermee = screen.getByRole("button", { name: "Caisse 2" }).closest("li") as HTMLElement;
    expect(within(fermee).getAllByText("—")).toHaveLength(3);
  });

  it("demande confirmation avant de désactiver une caisse ouverte", async () => {
    const requetes = afficher();
    await userEvent.click(await screen.findByRole("switch", { name: "Caisse 1 active" }));
    expect(screen.getByText(/sera clôturée d'office/)).toBeInTheDocument();
    expect(puts(requetes)).toHaveLength(0);

    await userEvent.click(screen.getByRole("button", { name: "Désactiver" }));
    await waitFor(() => expect(puts(requetes)).toHaveLength(1));
    expect(await puts(requetes)[0]?.json()).toEqual({ nom: "Caisse 1", active: false });
  });

  it("prévient aussi avant de désactiver une caisse fermée, et réactive sans confirmation", async () => {
    const requetes = afficher();
    await userEvent.click(await screen.findByRole("switch", { name: "Caisse 2 active" }));
    expect(screen.getByText("Plus personne ne pourra ouvrir cette caisse.")).toBeInTheDocument();
    expect(screen.queryByText(/sera clôturée d'office/)).not.toBeInTheDocument();
    expect(puts(requetes)).toHaveLength(0);
    await userEvent.click(screen.getByRole("button", { name: "Désactiver" }));
    await waitFor(() => expect(puts(requetes)).toHaveLength(1));

    await userEvent.click(screen.getByRole("switch", { name: "Ancienne active" }));
    await waitFor(() => expect(puts(requetes)).toHaveLength(2));
    expect(await puts(requetes)[1]?.json()).toEqual({ nom: "Ancienne", active: true });
  });

  it("renomme une caisse en cliquant sur son nom", async () => {
    const requetes = afficher();
    await userEvent.click(await screen.findByRole("button", { name: "Caisse 2" }));
    const nom = screen.getByLabelText("Nom");
    await userEvent.clear(nom);
    await userEvent.type(nom, "Comptoir");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));
    await waitFor(() => expect(puts(requetes)).toHaveLength(1));
    expect(await puts(requetes)[0]?.json()).toEqual({ nom: "Comptoir", active: true });
  });

  it("ne propose la suppression que pour une caisse inactive, après confirmation", async () => {
    const requetes = afficher();
    await screen.findByText("Ancienne");
    expect(screen.queryByRole("button", { name: "Supprimer Caisse 2" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Supprimer Ancienne" }));
    await userEvent.click(screen.getByRole("button", { name: "Supprimer" }));
    await waitFor(() => expect(requetes.some((r) => r.method === "DELETE")).toBe(true));
    const suppression = requetes.find((r) => r.method === "DELETE");
    expect(new URL(suppression?.url ?? "").pathname).toBe("/api/v1/caisses/3");
  });
});
