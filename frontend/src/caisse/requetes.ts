import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { appel } from "../api/erreurs";

export const cles = {
  sessionCourante: ["session-courante"] as const,
  caisses: ["caisses"] as const,
  parametres: ["parametres"] as const,
};

/** Session ouverte de l'utilisateur, ou null (réponse 204). */
export function useSessionCourante() {
  return useQuery({
    queryKey: cles.sessionCourante,
    queryFn: async () => (await appel(api.GET("/api/v1/sessions/courante"))) ?? null,
  });
}

export function useCaisses() {
  return useQuery({ queryKey: cles.caisses, queryFn: () => appel(api.GET("/api/v1/caisses")) });
}

/** Plafonds de remise, nom de la boutique et fuseau horaire. */
export function useParametres() {
  return useQuery({
    queryKey: cles.parametres,
    queryFn: () => appel(api.GET("/api/v1/parametres")),
    staleTime: 5 * 60_000,
  });
}

export function useOuvrirSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (corps: { caisseId: number; fondInitial: number }) =>
      appel(api.POST("/api/v1/sessions", { body: corps })),
    onSuccess: (session) => queryClient.setQueryData(cles.sessionCourante, session),
  });
}

export function useCloturerSession() {
  return useMutation({
    mutationFn: ({ id, montantCompte }: { id: number; montantCompte: number }) =>
      appel(api.POST("/api/v1/sessions/{id}/cloture", { params: { path: { id } }, body: { montantCompte } })),
  });
}
