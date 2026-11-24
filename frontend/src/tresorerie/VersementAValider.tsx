import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { decimal, dh, heure, lireMontant } from "../ui/format";
import { useRefuserVersement, useValiderVersement } from "./requetes";

interface Props {
  versement: Schemas["VersementDto"];
  /** Texte de l'émetteur : « Caisse principale » pour le caissier, le nom du caissier pour l'admin. */
  emetteur: string;
  fuseau: string | undefined;
  /** Caisse ouverte : le récepteur indique la part qu'il met dans son tiroir, le reste va sur son compte. */
  caisseOuverte?: boolean;
}

/** Versement en transit reçu : le récepteur confirme l'avoir reçu ou le refuse avec un motif. */
export function VersementAValider({ versement: v, emetteur, fuseau, caisseOuverte = false }: Props) {
  const valider = useValiderVersement();
  const refuser = useRefuserVersement();
  const [refus, setRefus] = useState<string | null>(null);
  const [saisieTiroir, setSaisieTiroir] = useState("");
  const tiroir = saisieTiroir.trim() === "" ? 0 : lireMontant(saisieTiroir);
  const tiroirValide = tiroir !== null && tiroir <= v.montant;
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
      {refus === null && caisseOuverte && (
        <div className="a-valider-tiroir">
          <label htmlFor={`a-valider-tiroir-${v.id}`}>Dans le tiroir</label>
          <div className="champ-montant">
            <input
              id={`a-valider-tiroir-${v.id}`}
              inputMode="decimal"
              autoComplete="off"
              placeholder={decimal(0)}
              value={saisieTiroir}
              onChange={(e) => setSaisieTiroir(e.target.value)}
            />
            <span>DH</span>
          </div>
          <span className="texte-secondaire">
            {tiroirValide
              ? `Sur mon compte : ${dh(Math.round((v.montant - (tiroir ?? 0)) * 100) / 100)}`
              : `Au plus ${dh(v.montant)} dans le tiroir.`}
          </span>
        </div>
      )}
      {refus === null ? (
        <div className="a-valider-actions">
          <button type="button" className="bouton-petit" disabled={occupe} onClick={() => setRefus("")}>
            Refuser
          </button>
          <button
            type="button"
            className="bouton-petit bouton-petit-plein"
            disabled={occupe || (caisseOuverte && !tiroirValide)}
            onClick={() => valider.mutate({ id: v.id, tiroir: caisseOuverte ? (tiroir ?? 0) : undefined })}
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
