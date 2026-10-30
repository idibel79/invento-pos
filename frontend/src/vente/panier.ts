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
}

export interface EtatPanier {
  lignes: LignePanier[];
  remiseTicket: Remise | null;
  motifRemise: string | null;
  /** Jeton remis après le PIN d'un responsable (15 min, lié à ce caissier). */
  jetonAutorisation: string | null;
  autorisePar: string | null;
  /** Vente en attente reprise : sa validation réutilise la même vente et son stock réservé. */
  venteEnAttenteId: number | null;
  numeroEnAttente: string | null;
}

export type ActionPanier =
  | { type: "ajouter"; produit: { id: number; designation: string; ppv: number } }
  | { type: "quantite"; produitId: number; quantite: number }
  | { type: "retirer"; produitId: number }
  | { type: "remiseLigne"; produitId: number; remise: Remise | null; motif: string | null }
  | { type: "remiseTicket"; remise: Remise | null; motif: string | null }
  | { type: "autorisation"; jeton: string; autorisePar: string }
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
          l.produitId === action.produitId ? { ...l, remise: sansRemiseNulle(action.remise) } : l,
        ),
      };
    case "remiseTicket":
      return {
        ...etat,
        remiseTicket: sansRemiseNulle(action.remise),
        motifRemise: action.motif ?? etat.motifRemise,
      };
    case "autorisation":
      return { ...etat, jetonAutorisation: action.jeton, autorisePar: action.autorisePar };
    case "reprendre":
      return {
        ...panierVide,
        lignes: action.vente.lignes.map((l) => ({
          produitId: l.produitId,
          designation: l.designation,
          prixUnitaire: l.prixUnitaire,
          quantite: l.quantite,
          remise: l.remise,
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
