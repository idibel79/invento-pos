import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { ErreurApi } from "./api/erreurs";
import { AuthProvider } from "./auth/AuthContext";
import "./styles.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      // Une erreur métier (4xx) ne se corrige pas en réessayant.
      retry: (essais, erreur) =>
        !(erreur instanceof ErreurApi && erreur.statut >= 400 && erreur.statut < 500) && essais < 2,
    },
  },
});

const racine = document.getElementById("root");
if (!racine) throw new Error("Élément #root absent");

createRoot(racine).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
