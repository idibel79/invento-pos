import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Schemas } from "../api/client";
import { Caisses } from "./Caisses";

const caisses: Schemas["CaisseDto"][] = [{ id: 1, nom: "Caisse 1", active: true }];
const avertissement = /sera clôturée d'office et ses ventes en attente annulées/;

function afficher() {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (requete: Request) => {
      const corps = new URL(requete.url).pathname === "/api/v1/caisses" ? caisses : [];
      return new Response(JSON.stringify(corps), {
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
}

afterEach(() => vi.unstubAllGlobals());

describe("Caisses", () => {
  it("prévient que désactiver une caisse active clôture sa session", async () => {
    afficher();
    await userEvent.click(await screen.findByRole("button", { name: "Modifier" }));
    expect(screen.queryByText(avertissement)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Inactive" }));
    expect(screen.getByText(avertissement)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Active" }));
    expect(screen.queryByText(avertissement)).not.toBeInTheDocument();
  });

  it("ne prévient pas pour une nouvelle caisse créée inactive", async () => {
    afficher();
    await userEvent.click(await screen.findByRole("button", { name: "Nouvelle caisse" }));
    await userEvent.click(screen.getByRole("button", { name: "Inactive" }));
    expect(screen.queryByText(avertissement)).not.toBeInTheDocument();
  });
});
