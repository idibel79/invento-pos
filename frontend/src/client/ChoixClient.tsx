import { useState } from "react";
import { messageErreur } from "../api/erreurs";
import { dh } from "../ui/format";
import { type Client, libelleSolde, useClients } from "./requetes";

/**
 * Recherche d'un client actif de l'annuaire, avec sa dette (visible par tous les rôles). Chaque client est
 * une option ; un clic le choisit. `seulementDettes` ne propose que les clients qui doivent quelque chose.
 */
export function ChoixClient({
  choisi,
  onChoisir,
  autoFocus = false,
  seulementDettes = false,
}: {
  choisi: Client | null;
  onChoisir: (client: Client) => void;
  autoFocus?: boolean;
  seulementDettes?: boolean;
}) {
  const [recherche, setRecherche] = useState("");
  // Un client désactivé peut encore rembourser sa dette.
  const clients = useClients(recherche, seulementDettes);
  const liste = (clients.data ?? []).filter((c) => !seulementDettes || c.dette > 0);

  return (
    <div className="choix-client">
      <input
        type="search"
        className="choix-client-recherche"
        aria-label="Rechercher un client"
        placeholder="Nom ou téléphone du client"
        autoComplete="off"
        autoFocus={autoFocus}
        value={recherche}
        onChange={(e) => setRecherche(e.target.value)}
      />
      {clients.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(clients.error)}
        </p>
      )}
      <div className="choix-client-liste" role="listbox" aria-label="Clients">
        {clients.isPending && <p className="vide">Chargement…</p>}
        {clients.isSuccess && liste.length === 0 && (
          <p className="vide">
            {seulementDettes
              ? "Aucun client ne correspond, ou aucun n'a de dette."
              : recherche.trim()
                ? "Aucun client ne correspond. Seul l'admin ajoute un client à l'annuaire."
                : "L'annuaire est vide. Seul l'admin ajoute un client (Administration › Clients)."}
          </p>
        )}
        {liste.map((c) => (
          <button
            key={c.id}
            type="button"
            role="option"
            aria-selected={c.id === choisi?.id}
            className="choix-client-option"
            onClick={() => onChoisir(c)}
          >
            <span className="choix-client-texte">
              <span className="choix-client-nom">{c.nom}</span>
              {c.telephone && <span className="texte-secondaire">{c.telephone}</span>}
            </span>
            <span className="choix-client-dette">
              <span className="texte-secondaire">{libelleSolde(c.dette)}</span>
              <strong className={c.dette > 0 ? "texte-dette" : "texte-secondaire"}>
                {dh(Math.abs(c.dette))}
              </strong>
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
