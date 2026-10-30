import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type Schemas } from "../api/client";
import { appel } from "../api/erreurs";
import { cles } from "../caisse/requetes";

const cleUtilisateurs = ["utilisateurs"] as const;

export function useUtilisateurs() {
  return useQuery({ queryKey: cleUtilisateurs, queryFn: () => appel(api.GET("/api/v1/utilisateurs")) });
}

export function useEnregistrerCaisse() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...corps }: { id: number | null; nom: string; active: boolean }) =>
      id === null
        ? appel(api.POST("/api/v1/caisses", { body: corps }))
        : appel(api.PUT("/api/v1/caisses/{id}", { params: { path: { id } }, body: corps })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: cles.caisses }),
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

export function useReinitialiserMotDePasse() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, motDePasseTemporaire }: { id: number; motDePasseTemporaire: string }) =>
      appel(
        api.POST("/api/v1/utilisateurs/{id}/mot-de-passe", {
          params: { path: { id } },
          body: { motDePasseTemporaire },
        }),
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: cleUtilisateurs }),
  });
}

export function useDefinirPin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, pin }: { id: number; pin: string }) =>
      appel(api.PUT("/api/v1/utilisateurs/{id}/pin", { params: { path: { id } }, body: { pin } })),
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
