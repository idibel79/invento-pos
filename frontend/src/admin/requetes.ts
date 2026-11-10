import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type Schemas } from "../api/client";
import { appel } from "../api/erreurs";
import { cles } from "../caisse/requetes";

const cleUtilisateurs = ["utilisateurs"] as const;

/** Comptes des utilisateurs, réservés à l'admin : `actif` à faux n'appelle pas le serveur. */
export function useUtilisateurs(actif = true) {
  return useQuery({
    queryKey: cleUtilisateurs,
    queryFn: () => appel(api.GET("/api/v1/utilisateurs")),
    enabled: actif,
  });
}

/** Caisses avec le fond et les espèces de leur session ouverte ; relu régulièrement pendant que l'écran est affiché. */
export function useSuiviCaisses() {
  return useQuery({
    queryKey: [...cles.caisses, "suivi"],
    queryFn: () => appel(api.GET("/api/v1/caisses/suivi")),
    refetchInterval: 30_000,
  });
}

export function useEnregistrerCaisse() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...corps }: { id: number | null; nom: string; active: boolean }) =>
      id === null
        ? appel(api.POST("/api/v1/caisses", { body: corps }))
        : appel(api.PUT("/api/v1/caisses/{id}", { params: { path: { id } }, body: corps })),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: cles.caisses });
      // L'admin a pu désactiver la caisse qu'il a lui-même ouverte : sa session est alors clôturée.
      void queryClient.invalidateQueries({ queryKey: cles.sessionCourante });
    },
  });
}

/** Suppression logique d'une caisse inactive ; elle est aussi retirée des fiches utilisateurs. */
export function useSupprimerCaisse() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => appel(api.DELETE("/api/v1/caisses/{id}", { params: { path: { id } } })),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: cles.caisses });
      void queryClient.invalidateQueries({ queryKey: cleUtilisateurs });
    },
  });
}

/** En-tête et pied du ticket (Paramètres > Boutique) ; la caisse relit les paramètres ensuite. */
export function useModifierParametres() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (corps: Schemas["ParametresCommande"]) =>
      appel(api.PUT("/api/v1/parametres", { body: corps })),
    onSuccess: (parametres) => queryClient.setQueryData(cles.parametres, parametres),
  });
}

export function useCreerUtilisateur() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (corps: Schemas["CreationUtilisateur"]) =>
      appel(api.POST("/api/v1/utilisateurs", { body: corps })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: cleUtilisateurs }),
  });
}

export function useModifierUtilisateur() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...corps }: Schemas["ModificationUtilisateur"] & { id: number }) =>
      appel(api.PUT("/api/v1/utilisateurs/{id}", { params: { path: { id } }, body: corps })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: cleUtilisateurs }),
  });
}

export function useDeverrouiller() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) =>
      appel(api.POST("/api/v1/utilisateurs/{id}/deverrouiller", { params: { path: { id } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: cleUtilisateurs }),
  });
}

/** Nouveau code de secours admin ; l'ancien est invalidé et le nouveau n'est montré qu'une fois. */
export function useNouveauCodeSecours() {
  return useMutation({
    mutationFn: async () => (await appel(api.POST("/api/v1/auth/code-secours"))).codeSecours,
  });
}

export interface DemandeImport {
  fichier: File;
  inventaire: string | null;
}

function multipart({ fichier, inventaire }: DemandeImport) {
  const formulaire = new FormData();
  formulaire.append("fichier", fichier);
  return {
    params: { query: inventaire ? { inventaire } : {} },
    // Le contrat décrit le fichier comme une chaîne binaire : le corps réellement envoyé est le FormData.
    body: { fichier: fichier.name },
    bodySerializer: () => formulaire,
  };
}

/** Lecture seule : format, inventaires proposés et erreurs, sans rien écrire. */
export function useAnalyserImport() {
  return useMutation({
    mutationFn: (demande: DemandeImport) =>
      appel(api.POST("/api/v1/imports/catalogue/analyse", multipart(demande))),
  });
}

export function useImporterCatalogue() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (demande: DemandeImport) => appel(api.POST("/api/v1/imports/catalogue", multipart(demande))),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["produits"] });
      void queryClient.invalidateQueries({ queryKey: ["zones"] });
      void queryClient.invalidateQueries({ queryKey: ["categories"] });
    },
  });
}

/** Filtres du journal des remises : les n derniers jours, une portée, une caisse, un vendeur (absent : pas de filtre). */
export interface FiltreRemises {
  jours?: number;
  portee?: "LIGNE" | "TICKET";
  caisseId?: number;
  vendeurId?: number;
}

const TAILLE_JOURNAL = 50;

export function useJournalRemises(filtre: FiltreRemises) {
  return useInfiniteQuery({
    queryKey: ["journal-remises", filtre],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      appel(
        api.GET("/api/v1/remises", {
          params: { query: { ...filtre, page: pageParam, taille: TAILLE_JOURNAL } },
        }),
      ),
    getNextPageParam: (derniere) => (derniere.page + 1 < derniere.totalPages ? derniere.page + 1 : undefined),
  });
}

export function useResumeRemises(filtre: FiltreRemises) {
  return useQuery({
    queryKey: ["journal-remises", "resume", filtre],
    queryFn: () => appel(api.GET("/api/v1/remises/resume", { params: { query: filtre } })),
  });
}

/** Filtres de l'historique des ventes : un jour (AAAA-MM-JJ, fuseau de la boutique), une caisse, un vendeur. */
export interface FiltreVentes {
  jour?: string;
  caisseId?: number;
  vendeurId?: number;
}

export function useHistoriqueVentes(filtre: FiltreVentes) {
  return useInfiniteQuery({
    queryKey: ["historique-ventes", filtre],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      appel(
        api.GET("/api/v1/historique-ventes", {
          params: { query: { ...filtre, page: pageParam, taille: TAILLE_JOURNAL } },
        }),
      ),
    getNextPageParam: (derniere) => (derniere.page + 1 < derniere.totalPages ? derniere.page + 1 : undefined),
  });
}

export function useResumeVentes(filtre: FiltreVentes) {
  return useQuery({
    queryKey: ["historique-ventes", "resume", filtre],
    queryFn: () => appel(api.GET("/api/v1/historique-ventes/resume", { params: { query: filtre } })),
  });
}

/** Une vente validée ne change plus : son détail reste en cache. */
export function useDetailVente(id: number) {
  return useQuery({
    queryKey: ["historique-ventes", "detail", id],
    queryFn: () => appel(api.GET("/api/v1/historique-ventes/{id}", { params: { path: { id } } })),
    staleTime: Infinity,
  });
}

/** Vente validée au format du ticket de caisse, pour le réimprimer. */
export function useTicketVente(id: number) {
  return useQuery({
    queryKey: ["historique-ventes", "ticket", id],
    queryFn: () => appel(api.GET("/api/v1/historique-ventes/{id}/ticket", { params: { path: { id } } })),
    staleTime: Infinity,
  });
}
