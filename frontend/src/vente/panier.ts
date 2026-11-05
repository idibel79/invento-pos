import type { Schemas } from "../api/client";

export type Remise = Schemas["RemiseDto"];

/** Ligne du panier tel que saisi ; les montants viennent toujours du serveur (/ventes/calcul). */
export interface LignePanier {
  produitId: number;
  designation: string;
  /** Prix affiché en attendant le calcul du serveur. */
  prixUnitaire: number;
  quantite: number;
  remise: Remise | null;
  /** Jeton du PIN qui autorise la remise de cet article (une autorisation par article). */
  jeton: string | null;
  autorisePar: string | null;
}

export interface EtatPanier {
  lignes: LignePanier[];
  remiseTicket: Remise | null;
  motifRemise: string | null;
  /** Jeton du PIN qui autorise la remise ticket (15 min, lié à ce caissier et à cette remise). */
  jetonAutorisation: string | null;
  autorisePar: string | null;
  /** Vente en attente reprise : sa validation réutilise la même vente (prix et remise autorisée). */
  venteEnAttenteId: number | null;
  numeroEnAttente: string | null;
}

export type ActionPanier =
  | { type: "ajouter"; produit: { id: number; designation: string; ppv: number } }
  | { type: "quantite"; produitId: number; quantite: number }
  | { type: "retirer"; produitId: number }
  | { type: "remiseLigne"; produitId: number; remise: Remise | null; motif: string | null }
  | { type: "remiseTicket"; remise: Remise | null; motif: string | null }
  | { type: "autorisation"; produitId: number | null; jeton: string; autorisePar: string }
  | { type: "reprendre"; vente: Schemas["VenteDto"] }
  | { type: "vider" };

export const panierVide: EtatPanier = {
  lignes: [],
  remiseTicket: null,
  motifRemise: null,
  jetonAutorisation: null,
  autorisePar: null,
  venteEnAttenteId: null,
  numeroEnAttente: null,
};

const QUANTITE_MAX = 9999;

function memeRemise(a: Remise | null, b: Remise | null): boolean {
  return a?.type === b?.type && a?.valeur === b?.valeur;
}

function sansRemiseNulle(remise: Remise | null): Remise | null {
  return remise && remise.valeur > 0 ? remise : null;
}

export function reducteurPanier(etat: EtatPanier, action: ActionPanier): EtatPanier {
  switch (action.type) {
    case "ajouter": {
      const existante = etat.lignes.find((l) => l.produitId === action.produit.id);
      if (existante) {
        return reducteurPanier(etat, {
          type: "quantite",
          produitId: existante.produitId,
          quantite: existante.quantite + 1,
        });
      }
      const ligne: LignePanier = {
        produitId: action.produit.id,
        designation: action.produit.designation,
        prixUnitaire: action.produit.ppv,
        quantite: 1,
        remise: null,
        jeton: null,
        autorisePar: null,
      };
      return { ...etat, lignes: [...etat.lignes, ligne] };
    }
    case "quantite": {
      if (action.quantite <= 0)
        return reducteurPanier(etat, { type: "retirer", produitId: action.produitId });
      const quantite = Math.min(Math.floor(action.quantite), QUANTITE_MAX);
      return {
        ...etat,
        lignes: etat.lignes.map((l) => (l.produitId === action.produitId ? { ...l, quantite } : l)),
      };
    }
    case "retirer": {
      const lignes = etat.lignes.filter((l) => l.produitId !== action.produitId);
      return lignes.length === 0 && etat.venteEnAttenteId === null ? panierVide : { ...etat, lignes };
    }
    case "remiseLigne":
      return {
        ...etat,
        motifRemise: action.motif ?? etat.motifRemise,
        lignes: etat.lignes.map((l) =>
          // Remise changée : l'autorisation donnée pour l'ancienne ne vaut plus.
          l.produitId === action.produitId
            ? memeRemise(l.remise, sansRemiseNulle(action.remise))
              ? l
              : { ...l, remise: sansRemiseNulle(action.remise), jeton: null, autorisePar: null }
            : l,
        ),
      };
    case "remiseTicket":
      return {
        ...etat,
        remiseTicket: sansRemiseNulle(action.remise),
        motifRemise: action.motif ?? etat.motifRemise,
        ...(memeRemise(etat.remiseTicket, sansRemiseNulle(action.remise))
          ? {}
          : { jetonAutorisation: null, autorisePar: null }),
      };
    case "autorisation":
      if (action.produitId === null)
        return { ...etat, jetonAutorisation: action.jeton, autorisePar: action.autorisePar };
      return {
        ...etat,
        lignes: etat.lignes.map((l) =>
          l.produitId === action.produitId
            ? { ...l, jeton: action.jeton, autorisePar: action.autorisePar }
            : l,
        ),
      };
    case "reprendre":
      return {
        ...panierVide,
        lignes: action.vente.lignes.map((l) => ({
          produitId: l.produitId,
          designation: l.designation,
          prixUnitaire: l.prixUnitaire,
          quantite: l.quantite,
          remise: l.remise,
          jeton: null,
          autorisePar: null,
        })),
        remiseTicket: action.vente.remiseTicket,
        motifRemise: action.vente.motifRemise,
        venteEnAttenteId: action.vente.id,
        numeroEnAttente: action.vente.numero,
      };
    case "vider":
      return panierVide;
  }
}

/** Panier envoyé au serveur : produits, quantités et remises, jamais de prix. */
export function versCommande(etat: EtatPanier): Schemas["PanierCommande"] {
  return {
    lignes: etat.lignes.map((l) => ({
      produitId: l.produitId,
      quantite: l.quantite,
      remise: l.remise ?? undefined,
      jetonAutorisation: l.jeton ?? undefined,
    })),
    remiseTicket: etat.remiseTicket ?? undefined,
    motifRemise: etat.motifRemise ?? undefined,
    jetonAutorisation: etat.jetonAutorisation ?? undefined,
    venteEnAttenteId: etat.venteEnAttenteId ?? undefined,
  };
}

export function nombreArticles(etat: EtatPanier): number {
  return etat.lignes.reduce((total, l) => total + l.quantite, 0);
}
