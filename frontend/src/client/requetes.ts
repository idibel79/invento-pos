import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type Schemas } from "../api/client";
import { appel } from "../api/erreurs";
import { dh } from "../ui/format";
import { cles as clesCaisse } from "../caisse/requetes";
import { clesTresorerie } from "../tresorerie/requetes";

export type Client = Schemas["ClientDto"];
export type MouvementClient = Schemas["MouvementClientDto"];

/** Dette négative : le client a payé d'avance (remboursement supérieur à sa dette). */
export function libelleSolde(dette: number): string {
  return dette < -0.005 ? "Avance" : "Dette";
}

/** Solde à afficher : la dette, ou « avance 50,00 DH » quand elle est négative. */
export function texteSolde(dette: number): string {
  return dette < -0.005 ? `avance ${dh(-dette)}` : dh(dette);
}

/** Clients et relevés partagent ce préfixe : une opération les relit tous. */
const CLIENTS = ["clients"] as const;

/** Annuaire (recherche sur le nom ou le téléphone) ; `inactifs` pour l'écran d'administration. */
export function useClients(recherche: string, inactifs = false) {
  const texte = recherche.trim();
  return useQuery({
    queryKey: [...CLIENTS, "liste", texte, inactifs],
    queryFn: () =>
      appel(api.GET("/api/v1/clients", { params: { query: { recherche: texte || undefined, inactifs } } })),
    placeholderData: keepPreviousData,
  });
}

export function useReleve(clientId: number | null) {
  return useQuery({
    queryKey: [...CLIENTS, "releve", clientId],
    queryFn: () => appel(api.GET("/api/v1/clients/{id}/releve", { params: { path: { id: clientId ?? 0 } } })),
    enabled: clientId !== null,
  });
}

/** Après une opération : annuaire et relevés relus. */
function useRelireClients() {
  const queryClient = useQueryClient();
  return () => void queryClient.invalidateQueries({ queryKey: CLIENTS });
}

export function useEnregistrerClient() {
  const relire = useRelireClients();
  return useMutation({
    mutationFn: ({ id, ...corps }: { id: number | null } & Schemas["ClientCommande"]) =>
      id === null
        ? appel(api.POST("/api/v1/clients", { body: corps }))
        : appel(api.PUT("/api/v1/clients/{id}", { params: { path: { id } }, body: corps })),
    onSuccess: relire,
  });
}

/** Supprime un client sans historique, sinon le désactive (réponse `supprime`). */
export function useSupprimerClient() {
  const relire = useRelireClients();
  return useMutation({
    mutationFn: (id: number) => appel(api.DELETE("/api/v1/clients/{id}", { params: { path: { id } } })),
    onSuccess: relire,
  });
}

/** Remboursement encaissé : la dette du client, le compte de l'utilisateur et l'attendu du tiroir changent. */
export function useRembourser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ clientId, ...corps }: { clientId: number; especes: number; tpe: number; cle: string }) =>
      appel(
        api.POST("/api/v1/clients/{id}/remboursements", { params: { path: { id: clientId } }, body: corps }),
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CLIENTS });
      void queryClient.invalidateQueries({ queryKey: clesTresorerie.tout });
      void queryClient.invalidateQueries({ queryKey: clesCaisse.sessionCourante });
    },
  });
}

export function useAnnulerRemboursement() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, motif }: { id: number; motif: string }) =>
      appel(
        api.POST("/api/v1/clients/remboursements/{id}/annulation", {
          params: { path: { id } },
          body: { motif },
        }),
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CLIENTS });
      void queryClient.invalidateQueries({ queryKey: clesTresorerie.tout });
      void queryClient.invalidateQueries({ queryKey: clesCaisse.sessionCourante });
    },
  });
}
