import { describe, expect, it } from "vitest";
import type { Schemas } from "../api/client";
import { nombreArticles, panierVide, reducteurPanier, versCommande, type EtatPanier } from "./panier";

const doliprane = { id: 1, designation: "DOLIPRANE 1000MG B8", ppv: 13.7 };
const cerave = { id: 2, designation: "CERAVE GEL 236ML", ppv: 125 };

function avec(...actions: Parameters<typeof reducteurPanier>[1][]): EtatPanier {
  return actions.reduce(reducteurPanier, panierVide);
}

describe("reducteurPanier", () => {
  it("un second scan du même produit augmente la quantité", () => {
    const etat = avec(
      { type: "ajouter", produit: doliprane },
      { type: "ajouter", produit: cerave },
      { type: "ajouter", produit: doliprane },
    );
    expect(etat.lignes.map((l) => [l.produitId, l.quantite])).toEqual([
      [1, 2],
      [2, 1],
    ]);
    expect(nombreArticles(etat)).toBe(3);
  });

  it("une quantité à zéro retire la ligne et le dernier retrait vide le panier", () => {
    const etat = avec(
      { type: "ajouter", produit: doliprane },
      { type: "remiseTicket", remise: { type: "POURCENT", valeur: 5 }, motif: null },
      { type: "quantite", produitId: 1, quantite: 0 },
    );
    expect(etat).toEqual(panierVide);
  });

  it("borne la quantité et ignore les décimales", () => {
    const etat = avec(
      { type: "ajouter", produit: doliprane },
      { type: "quantite", produitId: 1, quantite: 12000.7 },
    );
    expect(etat.lignes[0]?.quantite).toBe(9999);
  });

  it("une remise à zéro équivaut à aucune remise et le motif est conservé", () => {
    const etat = avec(
      { type: "ajouter", produit: cerave },
      { type: "remiseLigne", produitId: 2, remise: { type: "POURCENT", valeur: 15 }, motif: "Client fidèle" },
    );
    expect(etat.lignes[0]?.remise).toEqual({ type: "POURCENT", valeur: 15 });
    expect(etat.motifRemise).toBe("Client fidèle");

    const sansRemise = reducteurPanier(etat, {
      type: "remiseLigne",
      produitId: 2,
      remise: { type: "MONTANT", valeur: 0 },
      motif: null,
    });
    expect(sansRemise.lignes[0]?.remise).toBeNull();
    expect(sansRemise.motifRemise).toBe("Client fidèle");
  });

  it("la commande envoyée ne contient aucun prix", () => {
    const etat = avec(
      { type: "ajouter", produit: cerave },
      { type: "autorisation", jeton: "jeton", autorisePar: "Sara Alami" },
    );
    expect(versCommande(etat)).toEqual({
      lignes: [{ produitId: 2, quantite: 1, remise: undefined }],
      remiseTicket: undefined,
      motifRemise: undefined,
      jetonAutorisation: "jeton",
      venteEnAttenteId: undefined,
    });
  });

  it("reprend une vente en attente avec ses remises", () => {
    const vente = {
      id: 42,
      numero: "C1-000042",
      lignes: [
        {
          produitId: 2,
          designation: "CERAVE GEL 236ML",
          quantite: 2,
          prixUnitaire: 125,
          remise: { type: "MONTANT", valeur: 10 },
        },
      ],
      remiseTicket: null,
      motifRemise: null,
    } as unknown as Schemas["VenteDto"];
    const etat = reducteurPanier(avec({ type: "ajouter", produit: doliprane }), { type: "reprendre", vente });
    expect(etat.venteEnAttenteId).toBe(42);
    expect(etat.numeroEnAttente).toBe("C1-000042");
    expect(etat.lignes).toHaveLength(1);
    expect(versCommande(etat).venteEnAttenteId).toBe(42);
    expect(reducteurPanier(etat, { type: "vider" })).toEqual(panierVide);
  });
});
