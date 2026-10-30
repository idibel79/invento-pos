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

export interface FiltresCatalogue {
  recherche: string;
  zoneId: number | null;
  categorieId: number | null;
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
              actif: true,
              page: pageParam,
              size: TAILLE_PAGE,
              sort: ["designation,asc"],
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
