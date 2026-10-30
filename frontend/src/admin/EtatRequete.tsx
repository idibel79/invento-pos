import type { UseQueryResult } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { messageErreur } from "../api/erreurs";

interface Props<T> {
  requete: UseQueryResult<T[]>;
  vide: string;
  children: (liste: T[]) => ReactNode;
}

/** Chargement, erreur, liste vide ou contenu d'une liste lue sur le serveur. */
export function EtatRequete<T>({ requete, vide, children }: Props<T>) {
  if (requete.isPending) return <p className="vide">Chargement…</p>;
  if (requete.isError) {
    return (
      <div className="alerte alerte-danger" role="alert">
        {messageErreur(requete.error)}{" "}
        <button type="button" className="bouton-petit danger" onClick={() => void requete.refetch()}>
          Réessayer
        </button>
      </div>
    );
  }
  if (requete.data.length === 0) return <p className="vide">{vide}</p>;
  return children(requete.data);
}
