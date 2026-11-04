import { keepPreviousData, useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import { appel } from "../api/erreurs";

const TAILLE_PAGE = 24;

export function useZones() {
  return useQuery({
    queryKey: ["zones"],
    queryFn: () => appel(api.GET("/api/v1/zones")),
    staleTime: 5 * 60_000,
  });
}

export function useCategories() {
  return useQuery({
    queryKey: ["categories"],
    queryFn: () => appel(api.GET("/api/v1/categories")),
    staleTime: 5 * 60_000,
  });
}

/** Bornes du curseur de prix : prix de vente le plus bas et le plus haut du catalogue actif. */
export function useBornesPrix() {
  return useQuery({
    queryKey: ["produits", "bornes-prix"],
    queryFn: () => appel(api.GET("/api/v1/produits/bornes-prix")),
    staleTime: 5 * 60_000,
  });
}

/** Propriété de tri côté API pour chaque colonne triable. */
const PROPRIETES_TRI = {
  designation: "designation",
  zone: "zone.nom",
  categorie: "categorie.nom",
  stock: "stock",
  ppv: "ppv",
} as const;

export interface TriCatalogue {
  champ: keyof typeof PROPRIETES_TRI;
  ordre: "asc" | "desc";
}

export interface FiltresCatalogue {
  recherche: string;
  zoneId: number | null;
  categorieId: number | null;
  /** Plage de prix de vente TTC ; null : pas de limite de ce côté. */
  prixMin: number | null;
  prixMax: number | null;
  /** Tri fait par le serveur, sur toutes les pages ; null : ordre par défaut (désignation A → Z). */
  tri: TriCatalogue | null;
}

/** Critères envoyés à l'API ; à valeur égale, l'ordre alphabétique départage pour une liste stable. */
function ordreDeTri(tri: TriCatalogue | null): string[] {
  if (tri === null) return ["designation,asc"];
  const critere = `${PROPRIETES_TRI[tri.champ]},${tri.ordre}`;
  return tri.champ === "designation" ? [critere] : [critere, "designation,asc"];
}

/** Grille du catalogue (produits actifs), par pages de 24. */
export function useProduits(filtres: FiltresCatalogue) {
  return useInfiniteQuery({
    queryKey: ["produits", filtres],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      appel(
        api.GET("/api/v1/produits", {
          params: {
            query: {
              recherche: filtres.recherche || undefined,
              zoneId: filtres.zoneId ?? undefined,
              categorieId: filtres.categorieId ?? undefined,
              prixMin: filtres.prixMin ?? undefined,
              prixMax: filtres.prixMax ?? undefined,
              actif: true,
              page: pageParam,
              size: TAILLE_PAGE,
              sort: ordreDeTri(filtres.tri),
            },
          },
        }),
      ),
    getNextPageParam: (derniere) => (derniere.page + 1 < derniere.totalPages ? derniere.page + 1 : undefined),
    placeholderData: keepPreviousData,
  });
}

/** Scan de douchette : un seul aller-retour, sans attente. */
export function chercherParCodeBarre(code: string) {
  return appel(api.GET("/api/v1/produits/code-barre/{code}", { params: { path: { code } } }));
}
