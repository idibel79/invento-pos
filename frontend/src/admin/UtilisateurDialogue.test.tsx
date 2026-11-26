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
  seuilsRemise: { ligne: 0, ticket: 0 },
  derniereConnexion: null,
  version: 4,
};

function afficher(utilisateur: Schemas["UtilisateurDto"] | null, autres: Schemas["UtilisateurDto"][] = []) {
  const onFermer = vi.fn();
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <UtilisateurDialogue utilisateur={utilisateur} caisses={caisses} autres={autres} onFermer={onFermer} />
    </QueryClientProvider>,
  );
  return onFermer;
}

/** Comptes de trésorerie : Salma doit encore 120 DH à la caisse principale. */
const comptes = {
  soldePrincipale: 0,
  aValiderNombre: 0,
  aValiderMontant: 0,
  caissiers: [
    {
      utilisateurId: 5,
      nom: "Salma Bennani",
      role: "CAISSIER",
      actif: true,
      solde: 120,
      soldeEspeces: 120,
      soldeTpe: 0,
      sessionOuverteCaisse: null,
      ventesSessionOuverte: null,
      recusNombre: 0,
      recusMontant: 0,
      envoyesNombre: 0,
      envoyesMontant: 0,
    },
  ],
};

/** Simule l'API ; `requetes` ne garde que les enregistrements, pas les demandes d'identifiant proposé. */
function simulerServeur(statut = 200, corps: unknown = salma, identifiantPropose = "") {
  const requetes: Request[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (requete: Request) => {
      const chemin = new URL(requete.url).pathname;
      if (chemin.endsWith("/identifiant-propose")) {
        return new Response(JSON.stringify({ login: identifiantPropose }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      if (chemin === "/api/v1/tresorerie/comptes") {
        return new Response(JSON.stringify(comptes), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
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
    await userEvent.type(screen.getByLabelText("Identifiant de connexion"), "karim alaoui");
    await userEvent.type(screen.getByLabelText("Mot de passe temporaire"), "court");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));

    expect(await screen.findByText(/Lettres sans accent/)).toBeInTheDocument();
    expect(screen.getByText("8 caractères minimum")).toBeInTheDocument();
    expect(requetes).toHaveLength(0);
  });

  it("crée un caissier avec la caisse choisie, sans proposer les caisses inactives", async () => {
    const requetes = simulerServeur(201);
    const onFermer = afficher(null);

    expect(screen.queryByLabelText(/Ancienne/)).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Prénom"), "Karim");
    await userEvent.type(screen.getByLabelText("Nom"), "Alaoui");
    await userEvent.type(screen.getByLabelText("Identifiant de connexion"), "karim");
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
      pin: null,
      seuilsRemise: { ligne: 0, ticket: 0 },
    });
  });

  it("modifie un compte avec sa version et permet de remplacer une caisse devenue inactive", async () => {
    const requetes = simulerServeur();
    const onFermer = afficher(salma);

    expect(screen.getByLabelText("Identifiant de connexion")).toHaveValue("salma");
    await userEvent.click(screen.getByLabelText("Ancienne (inactive)"));
    await userEvent.click(screen.getByLabelText("Caisse 1"));
    await userEvent.click(screen.getByRole("button", { name: "Désactivé" }));
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));

    // Elle doit encore de l'argent : la désactivation est confirmée, montant dû affiché.
    expect(await screen.findByText(/Doit encore/)).toHaveTextContent("120,00");
    expect(requetes).toHaveLength(0);
    await userEvent.click(screen.getByRole("button", { name: "Désactiver" }));

    await waitFor(() => expect(onFermer).toHaveBeenCalled());
    expect(requetes[0]?.method).toBe("PUT");
    expect(new URL(requetes[0]?.url ?? "").pathname).toBe("/api/v1/utilisateurs/5");
    expect(await requetes[0]?.json()).toEqual({
      nom: "Bennani",
      prenom: "Salma",
      login: "salma",
      motDePasseTemporaire: null,
      pin: null,
      role: "CAISSIER",
      actif: false,
      caisseIds: [1],
      seuilsRemise: { ligne: 0, ticket: 0 },
      version: 4,
    });
  });

  it("change l'identifiant, le mot de passe, le PIN et les seuils d'un responsable", async () => {
    const requetes = simulerServeur();
    const onFermer = afficher({
      ...salma,
      role: "RESPONSABLE",
      pinDefini: true,
      seuilsRemise: { ligne: 10, ticket: 5 },
    });

    const identifiant = screen.getByLabelText("Identifiant de connexion");
    await userEvent.clear(identifiant);
    await userEvent.type(identifiant, "salma.b");
    await userEvent.type(screen.getByLabelText("Nouveau mot de passe temporaire"), "court");
    await userEvent.type(screen.getByLabelText("Nouveau PIN"), "12");
    const ticket = screen.getByLabelText(/Remise max sur le ticket/);
    expect(screen.getByLabelText(/Remise max par ligne/)).toHaveValue("10");
    await userEvent.clear(ticket);
    await userEvent.type(ticket, "120");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));

    expect(await screen.findByText("8 caractères minimum")).toBeInTheDocument();
    expect(screen.getByText("Exactement 4 chiffres")).toBeInTheDocument();
    expect(screen.getByText("Entre 0 et 100 %")).toBeInTheDocument();
    expect(requetes).toHaveLength(0);

    // Le bouton œil affiche la saisie.
    expect(screen.getByLabelText("Nouveau PIN")).toHaveAttribute("type", "password");
    await userEvent.click(screen.getByRole("button", { name: "Afficher le PIN" }));
    expect(screen.getByLabelText("Nouveau PIN")).toHaveAttribute("type", "text");

    await userEvent.type(screen.getByLabelText("Nouveau mot de passe temporaire"), "-Provisoire");
    await userEvent.type(screen.getByLabelText("Nouveau PIN"), "34");
    await userEvent.clear(ticket);
    await userEvent.type(ticket, "7,5");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));

    await waitFor(() => expect(onFermer).toHaveBeenCalled());
    expect(await requetes[0]?.json()).toMatchObject({
      login: "salma.b",
      motDePasseTemporaire: "court-Provisoire",
      pin: "1234",
      role: "RESPONSABLE",
      seuilsRemise: { ligne: 10, ticket: 7.5 },
    });
  });

  it("propose l'identifiant depuis le prénom et le nom tant qu'il n'est pas saisi", async () => {
    simulerServeur(200, salma, "karim.alaoui");
    afficher(null);

    await userEvent.type(screen.getByLabelText("Prénom"), "Karim");
    await userEvent.type(screen.getByLabelText("Nom"), "Alaoui");

    await waitFor(() =>
      expect(screen.getByLabelText("Identifiant de connexion")).toHaveValue("karim.alaoui"),
    );
  });

  it("ne propose ni PIN ni seuils pour un caissier", () => {
    simulerServeur();
    afficher(salma);

    expect(screen.queryByLabelText(/PIN/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Remise max/)).not.toBeInTheDocument();
    expect(screen.getByText(/Un caissier n'accorde aucune remise/)).toBeInTheDocument();
  });

  it("n'attribue qu'une caisse et grise celles d'un autre compte", async () => {
    simulerServeur();
    afficher(salma, [{ ...salma, id: 6, prenom: "Karim", nom: "Alaoui", caisseIds: [1] }]);

    expect(screen.getByLabelText(/Caisse 1 · attribuée à Karim Alaoui/)).toBeDisabled();
    await userEvent.click(screen.getByLabelText("Caisse 2"));
    expect(screen.getByLabelText("Caisse 2")).toBeChecked();
    expect(screen.queryByLabelText("Ancienne (inactive)")).not.toBeInTheDocument();
  });

  it("affiche le refus du serveur et garde la popup ouverte", async () => {
    simulerServeur(409, { detail: "L'identifiant karim est déjà utilisé." });
    const onFermer = afficher(null);

    await userEvent.type(screen.getByLabelText("Nom"), "Alaoui");
    await userEvent.type(screen.getByLabelText("Identifiant de connexion"), "karim");
    await userEvent.type(screen.getByLabelText("Mot de passe temporaire"), "Temporaire-2026");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("L'identifiant karim est déjà utilisé.");
    expect(onFermer).not.toHaveBeenCalled();
  });
});
