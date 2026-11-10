import { useMutation, useMutationState, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { appel, estSessionFermee, messageErreur } from "../api/erreurs";

export const cles = {
  sessionCourante: ["session-courante"] as const,
  caisses: ["caisses"] as const,
  parametres: ["parametres"] as const,
  /** Opérations qui exigent une session ouverte (vente, attente, clôture). */
  operationCaisse: ["operation-caisse"] as const,
};

/** Session ouverte de l'utilisateur, ou null (réponse 204). */
export function useSessionCourante() {
  return useQuery({
    queryKey: cles.sessionCourante,
    queryFn: async () => (await appel(api.GET("/api/v1/sessions/courante"))) ?? null,
  });
}

/**
 * À brancher sur les opérations de caisse : si le serveur répond que la session n'est plus ouverte
 * (caisse désactivée par l'admin), la session est relue et l'écran revient à l'ouverture.
 */
export function useSiSessionFermee() {
  const queryClient = useQueryClient();
  return (erreur: Error) => {
    if (estSessionFermee(erreur)) void queryClient.invalidateQueries({ queryKey: cles.sessionCourante });
  };
}

/** Dernier refus « session fermée », affiché sur l'écran d'ouverture de la caisse. */
export function useAvisSessionFermee(): string | null {
  const messages = useMutationState({
    filters: {
      mutationKey: cles.operationCaisse,
      status: "error",
      predicate: (mutation) => estSessionFermee(mutation.state.error),
    },
    select: (mutation) => messageErreur(mutation.state.error),
  });
  return messages.at(-1) ?? null;
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
    onSuccess: (session) => {
      // Nouvelle session : l'avis de la session clôturée d'office n'a plus lieu d'être.
      const mutations = queryClient.getMutationCache();
      mutations.findAll({ mutationKey: cles.operationCaisse }).forEach((m) => mutations.remove(m));
      queryClient.setQueryData(cles.sessionCourante, session);
      void queryClient.invalidateQueries({ queryKey: ["tresorerie"] });
    },
  });
}

export function useCloturerSession() {
  const siSessionFermee = useSiSessionFermee();
  const queryClient = useQueryClient();
  return useMutation({
    // Le compte du caissier change à la clôture (ventes, excédent, versement en transit).
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["tresorerie"] }),
    mutationKey: cles.operationCaisse,
    mutationFn: ({
      id,
      ...corps
    }: {
      id: number;
      montantCompteEspeces: number;
      montantCompteTpe: number;
      montantVerse: number;
    }) => appel(api.POST("/api/v1/sessions/{id}/cloture", { params: { path: { id } }, body: corps })),
    onError: siSessionFermee,
  });
}
