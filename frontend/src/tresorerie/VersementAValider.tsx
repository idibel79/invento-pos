import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { dh, heure } from "../ui/format";
import { useRefuserVersement, useValiderVersement } from "./requetes";

interface Props {
  versement: Schemas["VersementDto"];
  /** Texte de l'émetteur : « Caisse principale » pour le caissier, le nom du caissier pour l'admin. */
  emetteur: string;
  fuseau: string | undefined;
}

/** Versement en transit reçu : le récepteur confirme l'avoir reçu ou le refuse avec un motif. */
export function VersementAValider({ versement: v, emetteur, fuseau }: Props) {
  const valider = useValiderVersement();
  const refuser = useRefuserVersement();
  const [refus, setRefus] = useState<string | null>(null);
  const occupe = valider.isPending || refuser.isPending;
  const erreur = valider.error ?? refuser.error;

  return (
    <li className="a-valider">
      <div className="a-valider-ligne">
        <strong className="a-valider-montant">{dh(v.montant)}</strong>
        <span className="a-valider-detail">
          {emetteur} · {heure(v.creeLe, fuseau)}
          {v.cloture && " · clôture"}
          {v.motif && <span className="a-valider-motif">{v.motif}</span>}
        </span>
      </div>
      {refus === null ? (
        <div className="a-valider-actions">
          <button type="button" className="bouton-petit" disabled={occupe} onClick={() => setRefus("")}>
            Refuser
          </button>
          <button
            type="button"
            className="bouton-petit bouton-petit-plein"
            disabled={occupe}
            onClick={() => valider.mutate(v.id)}
          >
            J'ai reçu {dh(v.montant)}
          </button>
        </div>
      ) : (
        <form
          className="a-valider-refus"
          onSubmit={(e) => {
            e.preventDefault();
            e.stopPropagation();
            if (refus.trim()) refuser.mutate({ id: v.id, motif: refus.trim() });
          }}
        >
          <input
            aria-label="Motif du refus"
            placeholder="Motif du refus (obligatoire)"
            maxLength={300}
            value={refus}
            onChange={(e) => setRefus(e.target.value)}
            autoFocus
          />
          <button type="button" className="bouton-petit" onClick={() => setRefus(null)}>
            Retour
          </button>
          <button
            type="submit"
            className="bouton-petit bouton-petit-danger"
            disabled={!refus.trim() || occupe}
          >
            Refuser
          </button>
        </form>
      )}
      {erreur && (
        <p className="erreur-champ" role="alert">
          {messageErreur(erreur)}
        </p>
      )}
    </li>
  );
}
