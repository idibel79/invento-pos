import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { appel } from "../api/erreurs";
import { cles as clesCaisse } from "../caisse/requetes";

/** Toutes les données de trésorerie partagent ce préfixe : une opération les relit toutes. */
const TRESORERIE = ["tresorerie"] as const;
const TAILLE_JOURNAL = 50;

export const clesTresorerie = {
  tout: TRESORERIE,
  monCompte: [...TRESORERIE, "mon-compte"] as const,
  comptes: [...TRESORERIE, "comptes"] as const,
  aValider: [...TRESORERIE, "a-valider"] as const,
  comptages: [...TRESORERIE, "comptages-en-attente"] as const,
};

export type StatutJournal = "EN_TRANSIT" | "VALIDE" | "AUTO_VALIDE" | "REFUSE" | "ANNULE";

export interface FiltreJournal {
  jour?: string;
  caisseId?: number;
  utilisateurId?: number;
  statut?: StatutJournal;
  croissant?: boolean;
}

/** Après une opération : soldes, versements, journal et session (attendu du tiroir) sont relus. */
function useApresOperation() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: TRESORERIE });
    void queryClient.invalidateQueries({ queryKey: clesCaisse.sessionCourante });
  };
}

/** Clé d'idempotence d'une saisie : un double clic ou un renvoi ne crée pas deux opérations. */
export function nouvelleCle(): string {
  return crypto.randomUUID();
}

// ---------------------------------------------------------------- Compte de l'utilisateur connecté

export function useMonCompte() {
  return useQuery({
    queryKey: clesTresorerie.monCompte,
    queryFn: () => appel(api.GET("/api/v1/tresorerie/mon-compte")),
    refetchInterval: 30_000,
  });
}

export function useVerserALaPrincipale() {
  const apres = useApresOperation();
  return useMutation({
    mutationFn: (corps: { montant: number; cle: string }) =>
      appel(api.POST("/api/v1/tresorerie/mon-compte/versements", { body: corps })),
    onSuccess: apres,
  });
}

export function useValiderVersement() {
  const apres = useApresOperation();
  return useMutation({
    mutationFn: (id: number) =>
      appel(api.POST("/api/v1/tresorerie/versements/{id}/validation", { params: { path: { id } } })),
    onSettled: apres,
  });
}

export function useRefuserVersement() {
  const apres = useApresOperation();
  return useMutation({
    mutationFn: ({ id, motif }: { id: number; motif: string }) =>
      appel(
        api.POST("/api/v1/tresorerie/versements/{id}/refus", { params: { path: { id } }, body: { motif } }),
      ),
    onSettled: apres,
  });
}

export function useAnnulerVersement() {
  const apres = useApresOperation();
  return useMutation({
    mutationFn: (id: number) =>
      appel(api.POST("/api/v1/tresorerie/versements/{id}/annulation", { params: { path: { id } } })),
    onSettled: apres,
  });
}

// ---------------------------------------------------------------- Caisse principale (admin)

export function useComptes() {
  return useQuery({
    queryKey: clesTresorerie.comptes,
    queryFn: () => appel(api.GET("/api/v1/tresorerie/comptes")),
    refetchInterval: 30_000,
  });
}

export function useAValiderPrincipale() {
  return useQuery({
    queryKey: clesTresorerie.aValider,
    queryFn: () => appel(api.GET("/api/v1/tresorerie/principale/a-valider")),
    refetchInterval: 30_000,
  });
}

export function useVersementsCaissier(utilisateurId: number | undefined) {
  return useQuery({
    queryKey: [...TRESORERIE, "versements", utilisateurId],
    queryFn: () =>
      appel(
        api.GET("/api/v1/tresorerie/comptes/{utilisateurId}/versements", {
          params: { path: { utilisateurId: utilisateurId ?? 0 } },
        }),
      ),
    enabled: utilisateurId !== undefined,
  });
}

/** Sessions clôturées d'office dont le tiroir attend le comptage de l'admin. */
export function useComptagesEnAttente() {
  return useQuery({
    queryKey: clesTresorerie.comptages,
    queryFn: () => appel(api.GET("/api/v1/sessions/comptages-en-attente")),
  });
}

interface Operation {
  montant: number;
  motif?: string;
  cle: string;
}

export function useAlimenter() {
  const apres = useApresOperation();
  return useMutation({
    mutationFn: (corps: Operation) =>
      appel(api.POST("/api/v1/tresorerie/principale/alimentation", { body: corps })),
    onSuccess: apres,
  });
}

export function useRetirer() {
  const apres = useApresOperation();
  return useMutation({
    mutationFn: (corps: Operation) =>
      appel(api.POST("/api/v1/tresorerie/principale/retrait", { body: corps })),
    onSuccess: apres,
  });
}

export function useVerserAuCaissier() {
  const apres = useApresOperation();
  return useMutation({
    mutationFn: (corps: Operation & { utilisateurId: number }) =>
      appel(api.POST("/api/v1/tresorerie/principale/versements", { body: corps })),
    onSuccess: apres,
  });
}

export function useAjuster() {
  const apres = useApresOperation();
  return useMutation({
    mutationFn: ({ utilisateurId, ...corps }: Operation & { utilisateurId: number }) =>
      appel(
        api.POST("/api/v1/tresorerie/comptes/{utilisateurId}/ajustement", {
          params: { path: { utilisateurId } },
          body: corps,
        }),
      ),
    onSuccess: apres,
  });
}

export function useTerminerComptage() {
  const queryClient = useQueryClient();
  const apres = useApresOperation();
  return useMutation({
    mutationFn: ({ id, ...corps }: { id: number; montantCompte: number; montantVerse: number }) =>
      appel(api.POST("/api/v1/sessions/{id}/comptage", { params: { path: { id } }, body: corps })),
    onSuccess: () => {
      apres();
      void queryClient.invalidateQueries({ queryKey: clesCaisse.caisses });
    },
  });
}

/**
 * Journal de la caisse principale (admin) ou, avec `monCompte`, celui de l'utilisateur connecté : ses
 * versements et alimentations (ventes de la session sur un versement de clôture), sans filtre de caisse ni d'utilisateur.
 */
export function useJournalTresorerie(filtre: FiltreJournal, monCompte = false) {
  return useInfiniteQuery({
    queryKey: [...TRESORERIE, monCompte ? "journal-mon-compte" : "journal", filtre],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => {
      const { jour, statut, croissant } = filtre;
      return appel(
        monCompte
          ? api.GET("/api/v1/tresorerie/mon-compte/journal", {
              params: { query: { jour, statut, croissant, page: pageParam, taille: TAILLE_JOURNAL } },
            })
          : api.GET("/api/v1/tresorerie/journal", {
              params: { query: { ...filtre, page: pageParam, taille: TAILLE_JOURNAL } },
            }),
      );
    },
    getNextPageParam: (derniere) => (derniere.page + 1 < derniere.totalPages ? derniere.page + 1 : undefined),
  });
}
