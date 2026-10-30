import { useQueryClient } from "@tanstack/react-query";
import { createContext, use, useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, definirJetonAcces, quandSessionExpire, rafraichirSession, type Schemas } from "../api/client";
import { appel } from "../api/erreurs";

type Utilisateur = Schemas["UtilisateurDto"];

interface Auth {
  statut: "chargement" | "deconnecte" | "connecte";
  utilisateur: Utilisateur | null;
  connecter: (login: string, motDePasse: string) => Promise<void>;
  changerMotDePasse: (actuel: string, nouveau: string) => Promise<void>;
  deconnecter: () => Promise<void>;
}

const AuthContext = createContext<Auth | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [statut, setStatut] = useState<Auth["statut"]>("chargement");
  const [utilisateur, setUtilisateur] = useState<Utilisateur | null>(null);

  const ouvrir = useCallback((session: Schemas["SessionDto"]) => {
    definirJetonAcces(session.accessToken);
    setUtilisateur(session.utilisateur);
    setStatut("connecte");
  }, []);

  const fermer = useCallback(() => {
    definirJetonAcces(null);
    setUtilisateur(null);
    setStatut("deconnecte");
    queryClient.clear();
  }, [queryClient]);

  // Au chargement de la page, le cookie de rafraîchissement rouvre la session s'il est valide.
  useEffect(() => {
    quandSessionExpire(fermer);
    void rafraichirSession().then((session) => (session ? ouvrir(session) : setStatut("deconnecte")));
  }, [fermer, ouvrir]);

  const valeur = useMemo<Auth>(
    () => ({
      statut,
      utilisateur,
      connecter: async (login, motDePasse) =>
        ouvrir(await appel(api.POST("/api/v1/auth/connexion", { body: { login, motDePasse } }))),
      changerMotDePasse: async (motDePasseActuel, nouveauMotDePasse) =>
        ouvrir(
          await appel(
            api.POST("/api/v1/auth/mot-de-passe", { body: { motDePasseActuel, nouveauMotDePasse } }),
          ),
        ),
      deconnecter: async () => {
        try {
          await api.POST("/api/v1/auth/deconnexion");
        } finally {
          fermer();
        }
      },
    }),
    [statut, utilisateur, ouvrir, fermer],
  );

  return <AuthContext value={valeur}>{children}</AuthContext>;
}

export function useAuth(): Auth {
  const auth = use(AuthContext);
  if (!auth) throw new Error("useAuth hors de AuthProvider");
  return auth;
}

/** Utilisateur connecté (écrans affichés seulement après connexion). */
export function useUtilisateur(): Utilisateur {
  const { utilisateur } = useAuth();
  if (!utilisateur) throw new Error("Aucun utilisateur connecté");
  return utilisateur;
}
