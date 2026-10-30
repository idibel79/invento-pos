import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Schemas } from "../api/client";
import { UtilisateurDialogue } from "./UtilisateurDialogue";

const caisses: Schemas["CaisseDto"][] = [
  { id: 1, nom: "Caisse 1", active: true },
  { id: 2, nom: "Caisse 2", active: true },
  { id: 3, nom: "Ancienne", active: false },
];

const salma: Schemas["UtilisateurDto"] = {
  id: 5,
  nom: "Bennani",
  prenom: "Salma",
  login: "salma",
  role: "CAISSIER",
  actif: true,
  verrouille: false,
  doitChangerMotDePasse: false,
  pinDefini: false,
  caisseIds: [3],
  derniereConnexion: null,
  version: 4,
};

function afficher(utilisateur: Schemas["UtilisateurDto"] | null) {
  const onFermer = vi.fn();
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <UtilisateurDialogue utilisateur={utilisateur} caisses={caisses} onFermer={onFermer} />
    </QueryClientProvider>,
  );
  return onFermer;
}

function simulerServeur(statut = 200, corps: unknown = salma) {
  const requetes: Request[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (requete: Request) => {
      requetes.push(requete);
      return new Response(JSON.stringify(corps), {
        status: statut,
        headers: { "Content-Type": "application/json" },
      });
    }),
  );
  return requetes;
}

afterEach(() => vi.unstubAllGlobals());

describe("UtilisateurDialogue", () => {
  it("guide la saisie d'un nouveau compte sans appeler le serveur", async () => {
    const requetes = simulerServeur();
    afficher(null);

    await userEvent.type(screen.getByLabelText("Nom"), "Alaoui");
    await userEvent.type(screen.getByLabelText("Identifiant"), "karim alaoui");
    await userEvent.type(screen.getByLabelText("Mot de passe temporaire"), "court");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));

    expect(await screen.findByText(/Lettres sans accent/)).toBeInTheDocument();
    expect(screen.getByText("8 caractères minimum")).toBeInTheDocument();
    expect(requetes).toHaveLength(0);
  });

  it("crée un caissier avec les caisses cochées, sans proposer les caisses inactives", async () => {
    const requetes = simulerServeur(201);
    const onFermer = afficher(null);

    expect(screen.queryByLabelText(/Ancienne/)).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Prénom"), "Karim");
    await userEvent.type(screen.getByLabelText("Nom"), "Alaoui");
    await userEvent.type(screen.getByLabelText("Identifiant"), "karim");
    await userEvent.type(screen.getByLabelText("Mot de passe temporaire"), "Temporaire-2026");
    await userEvent.click(screen.getByRole("button", { name: "Responsable" }));
    await userEvent.click(screen.getByLabelText("Caisse 2"));
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));

    await waitFor(() => expect(onFermer).toHaveBeenCalled());
    expect(requetes[0]?.method).toBe("POST");
    expect(await requetes[0]?.json()).toEqual({
      nom: "Alaoui",
      prenom: "Karim",
      login: "karim",
      role: "RESPONSABLE",
      motDePasseTemporaire: "Temporaire-2026",
      caisseIds: [2],
    });
  });

  it("modifie un compte avec sa version et permet de retirer une caisse devenue inactive", async () => {
    const requetes = simulerServeur();
    const onFermer = afficher(salma);

    expect(screen.queryByLabelText("Identifiant")).not.toBeInTheDocument();
    await userEvent.click(screen.getByLabelText("Ancienne (inactive)"));
    await userEvent.click(screen.getByLabelText("Caisse 1"));
    await userEvent.click(screen.getByRole("button", { name: "Désactivé" }));
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));

    await waitFor(() => expect(onFermer).toHaveBeenCalled());
    expect(requetes[0]?.method).toBe("PUT");
    expect(new URL(requetes[0]?.url ?? "").pathname).toBe("/api/v1/utilisateurs/5");
    expect(await requetes[0]?.json()).toEqual({
      nom: "Bennani",
      prenom: "Salma",
      role: "CAISSIER",
      actif: false,
      caisseIds: [1],
      version: 4,
    });
  });

  it("affiche le refus du serveur et garde la popup ouverte", async () => {
    simulerServeur(409, { detail: "L'identifiant karim est déjà utilisé." });
    const onFermer = afficher(null);

    await userEvent.type(screen.getByLabelText("Nom"), "Alaoui");
    await userEvent.type(screen.getByLabelText("Identifiant"), "karim");
    await userEvent.type(screen.getByLabelText("Mot de passe temporaire"), "Temporaire-2026");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("L'identifiant karim est déjà utilisé.");
    expect(onFermer).not.toHaveBeenCalled();
  });
});
