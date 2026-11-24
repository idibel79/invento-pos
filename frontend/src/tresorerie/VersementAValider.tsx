import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { arrondi } from "../caisse/cloture";
import { decimal, dh, heure, lireMontant } from "../ui/format";
import { useRefuserVersement, useValiderVersement } from "./requetes";

interface Props {
  versement: Schemas["VersementDto"];
  /** Texte de l'émetteur : « Caisse principale » pour le caissier, le nom du caissier pour l'admin. */
  emetteur: string;
  fuseau: string | undefined;
  /** Caisse ouverte : le récepteur choisit où va l'argent, dans son tiroir, sur lui, ou une partie de chaque. */
  caisseOuverte?: boolean;
}

type Destination = "tiroir" | "moi" | "partager";

const DESTINATIONS: { valeur: Destination; titre: string; aide: string }[] = [
  { valeur: "tiroir", titre: "Dans le tiroir", aide: "Compté à la clôture" },
  { valeur: "moi", titre: "Sur moi", aide: "Ajouté à mon solde dû" },
  { valeur: "partager", titre: "Partager", aide: "Une partie de chaque" },
];

/** Versement en transit reçu : le récepteur confirme l'avoir reçu ou le refuse avec un motif. */
export function VersementAValider({ versement: v, emetteur, fuseau, caisseOuverte = false }: Props) {
  const valider = useValiderVersement();
  const refuser = useRefuserVersement();
  const [refus, setRefus] = useState<string | null>(null);
  const [destination, setDestination] = useState<Destination | null>(null);
  const [saisieTiroir, setSaisieTiroir] = useState("");
  const occupe = valider.isPending || refuser.isPending;
  const erreur = valider.error ?? refuser.error;

  const saisie = saisieTiroir.trim() === "" ? null : lireMontant(saisieTiroir);
  const tiroir =
    destination === "tiroir"
      ? v.montant
      : destination === "moi"
        ? 0
        : saisie !== null && saisie <= v.montant
          ? saisie
          : null;
  const surMoi = tiroir === null ? null : arrondi(v.montant - tiroir);
  const pret = !caisseOuverte || tiroir !== null;

  return (
    <li className="a-valider">
      <div className="a-valider-ligne">
        <span className="a-valider-detail">
          Reçu de la {emetteur.toLowerCase()} · {heure(v.creeLe, fuseau)}
          {v.cloture && " · clôture"}
          {v.motif && <span className="a-valider-motif">« {v.motif} »</span>}
        </span>
        <strong className="a-valider-montant">{dh(v.montant)}</strong>
      </div>

      {refus === null && caisseOuverte && (
        <>
          <fieldset className="a-valider-choix">
            <legend>Où mets-tu cet argent ?</legend>
            <div className="a-valider-options">
              {DESTINATIONS.map((d) => (
                <label
                  key={d.valeur}
                  className={destination === d.valeur ? "a-valider-option choisie" : "a-valider-option"}
                >
                  <span className="a-valider-option-titre">
                    <input
                      type="radio"
                      name={`a-valider-destination-${v.id}`}
                      value={d.valeur}
                      checked={destination === d.valeur}
                      onChange={() => setDestination(d.valeur)}
                    />
                    {d.titre}
                  </span>
                  <span className="a-valider-option-aide">{d.aide}</span>
                </label>
              ))}
            </div>
          </fieldset>

          {destination === "partager" && (
            <div className="a-valider-partage">
              <div className="a-valider-partage-champ">
                <label htmlFor={`a-valider-tiroir-${v.id}`}>Dans le tiroir</label>
                <div className="a-valider-saisie">
                  <input
                    id={`a-valider-tiroir-${v.id}`}
                    inputMode="decimal"
                    autoComplete="off"
                    placeholder={decimal(0)}
                    value={saisieTiroir}
                    onChange={(e) => setSaisieTiroir(e.target.value)}
                    aria-invalid={saisie !== null && saisie > v.montant}
                    autoFocus
                  />
                  <span>DH</span>
                </div>
              </div>
              <div className="a-valider-partage-champ">
                <span>Sur moi</span>
                <output className="a-valider-sur-moi" htmlFor={`a-valider-tiroir-${v.id}`}>
                  {surMoi === null ? "–" : decimal(surMoi)} <span>DH</span>
                </output>
              </div>
              {saisie !== null && saisie > v.montant && (
                <p className="erreur-champ a-valider-note">Au plus {dh(v.montant)} dans le tiroir.</p>
              )}
            </div>
          )}

          {tiroir !== null && (
            <p className="a-valider-note texte-secondaire">
              {tiroir > 0 ? (
                <>
                  À la clôture, le tiroir devra contenir <strong>{dh(tiroir)}</strong> de plus.
                </>
              ) : (
                "Ajouté à mon solde dû, rien ne change dans le tiroir."
              )}
            </p>
          )}
        </>
      )}

      {refus === null ? (
        <div className="a-valider-actions">
          <button type="button" className="bouton-petit" disabled={occupe} onClick={() => setRefus("")}>
            Refuser…
          </button>
          <button
            type="button"
            className="bouton-petit bouton-petit-plein"
            disabled={occupe || !pret}
            onClick={() => valider.mutate({ id: v.id, tiroir: caisseOuverte ? (tiroir ?? 0) : undefined })}
          >
            {libelleConfirmation(caisseOuverte, destination, v.montant, tiroir, surMoi)}
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

function libelleConfirmation(
  caisseOuverte: boolean,
  destination: Destination | null,
  montant: number,
  tiroir: number | null,
  surMoi: number | null,
): string {
  if (!caisseOuverte) return `J'ai reçu ${dh(montant)}`;
  if (destination === "tiroir") return "Confirmer : tout dans le tiroir";
  if (destination === "moi") return "Confirmer : tout sur moi";
  if (tiroir !== null && surMoi !== null)
    return `Confirmer : ${decimal(tiroir)} au tiroir, ${decimal(surMoi)} sur moi`;
  return "Confirmer la réception";
}
