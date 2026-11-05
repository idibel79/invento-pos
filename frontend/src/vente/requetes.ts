import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type Schemas } from "../api/client";
import { appel } from "../api/erreurs";
import { cles, useSiSessionFermee } from "../caisse/requetes";

const cleAttente = ["ventes-en-attente"] as const;

/** Calcul serveur du panier (totaux, TVA, remises, stock, autorisation requise). */
export function usePanierCalcule(commande: Schemas["PanierCommande"]) {
  return useQuery({
    queryKey: ["panier-calcule", commande],
    queryFn: () => appel(api.POST("/api/v1/ventes/calcul", { body: commande })),
    enabled: commande.lignes.length > 0,
    placeholderData: keepPreviousData,
    retry: false,
  });
}

/** Ventes en attente de la session ; rafraîchies pour refléter les échéances (5 min). */
export function useVentesEnAttente() {
  return useQuery({
    queryKey: cleAttente,
    queryFn: () => appel(api.GET("/api/v1/ventes/attente")),
    refetchInterval: 15_000,
  });
}

/** Rafraîchit ce que chaque opération de vente change : attentes, stock affiché, aperçu. */
function useInvalidationVente() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: cleAttente });
    void queryClient.invalidateQueries({ queryKey: ["produits"] });
    queryClient.removeQueries({ queryKey: ["panier-calcule"] });
  };
}

export function useValiderVente() {
  const queryClient = useQueryClient();
  const invalider = useInvalidationVente();
  const siSessionFermee = useSiSessionFermee();
  return useMutation({
    mutationKey: cles.operationCaisse,
    onError: siSessionFermee,
    mutationFn: ({ cle, corps }: { cle: string; corps: Schemas["ValidationCommande"] }) =>
      appel(api.POST("/api/v1/ventes", { params: { header: { "Idempotency-Key": cle } }, body: corps })),
    onSuccess: () => {
      invalider();
      // Contenu du tiroir affiché dans la barre de caisse, et solde du compte (ventes comprises).
      void queryClient.invalidateQueries({ queryKey: cles.sessionCourante });
      void queryClient.invalidateQueries({ queryKey: ["tresorerie"] });
    },
  });
}

export function useMettreEnAttente() {
  const invalider = useInvalidationVente();
  const siSessionFermee = useSiSessionFermee();
  return useMutation({
    mutationKey: cles.operationCaisse,
    onError: siSessionFermee,
    mutationFn: ({ cle, corps }: { cle: string; corps: Schemas["MiseEnAttenteCommande"] }) =>
      appel(
        api.POST("/api/v1/ventes/attente", { params: { header: { "Idempotency-Key": cle } }, body: corps }),
      ),
    onSuccess: invalider,
  });
}

export function useReprendreVente() {
  const invalider = useInvalidationVente();
  const siSessionFermee = useSiSessionFermee();
  return useMutation({
    mutationKey: cles.operationCaisse,
    onError: siSessionFermee,
    mutationFn: (id: number) => appel(api.POST("/api/v1/ventes/{id}/reprise", { params: { path: { id } } })),
    onSuccess: invalider,
  });
}

export function useAbandonnerVente() {
  const invalider = useInvalidationVente();
  const siSessionFermee = useSiSessionFermee();
  return useMutation({
    mutationKey: cles.operationCaisse,
    onError: siSessionFermee,
    mutationFn: (id: number) => appel(api.POST("/api/v1/ventes/{id}/abandon", { params: { path: { id } } })),
    onSuccess: invalider,
  });
}

export function useAutorisateurs(actif: boolean) {
  return useQuery({
    queryKey: ["autorisateurs"],
    queryFn: () => appel(api.GET("/api/v1/autorisations/autorisateurs")),
    enabled: actif,
  });
}

export function useAutoriserRemise() {
  return useMutation({
    mutationFn: (corps: Schemas["DemandeAutorisation"]) =>
      appel(api.POST("/api/v1/autorisations/remise", { body: corps })),
  });
}
