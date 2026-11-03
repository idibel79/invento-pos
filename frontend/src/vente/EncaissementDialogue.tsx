import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { decimal, dh, lireMontant } from "../ui/format";
import { useValiderVente } from "./requetes";

interface Props {
  commande: Schemas["PanierCommande"];
  total: number;
  onValidee: (vente: Schemas["VenteDto"]) => void;
  onFermer: () => void;
}

/** Billets en circulation : chaque clic ajoute le billet au montant reçu. */
export const BILLETS = [20, 50, 100, 200] as const;

/** Montant reçu après l'ajout d'un billet (un montant vide compte pour zéro). */
export function ajouterBillet(recu: number | null, billet: number): number {
  return Math.round(((recu ?? 0) + billet) * 100) / 100;
}

/**
 * Encaissement en espèces (carte et paiement mixte : étape 3). Le rendu affiché aide le caissier ;
 * celui du ticket est calculé par le serveur.
 */
export function EncaissementDialogue({ commande, total, onValidee, onFermer }: Props) {
  // Une clé par encaissement : un nouvel essai après une coupure ne crée pas une seconde vente.
  const [cle] = useState(() => crypto.randomUUID());
  const [saisie, setSaisie] = useState("");
  const valider = useValiderVente();
  const recu = saisie === "" ? null : lireMontant(saisie);
  const rendu = recu === null ? null : recu - total;
  const suffisant = recu !== null && rendu !== null && rendu >= -0.005;

  function choisir(montant: number) {
    setSaisie(decimal(montant));
  }

  return (
    <Dialogue titre="Encaissement" sousTitre="Paiement en espèces" onFermer={onFermer}>
      <div className="total-a-payer">
        <span>Total à payer</span>
        <strong>{dh(total)}</strong>
      </div>
      <div className="champ">
        <span className="libelle">Billets reçus (chaque clic s'ajoute)</span>
        <div className="billets" role="group" aria-label="Billets reçus">
          {BILLETS.map((b) => (
            <button
              key={b}
              type="button"
              aria-label={`Ajouter un billet de ${b} DH`}
              onClick={() => setSaisie((s) => decimal(ajouterBillet(s === "" ? null : lireMontant(s), b)))}
            >
              + {b} DH
            </button>
          ))}
        </div>
      </div>
      <label className="libelle" htmlFor="montant-recu">
        Montant reçu
      </label>
      <div className="champ-montant">
        <input
          id="montant-recu"
          inputMode="decimal"
          autoComplete="off"
          value={saisie}
          onChange={(e) => setSaisie(e.target.value)}
          placeholder={decimal(total)}
          autoFocus
        />
        <span>DH</span>
      </div>
      <div className="raccourcis-montant">
        <button type="button" onClick={() => choisir(total)}>
          Exact
        </button>
        <button type="button" onClick={() => setSaisie("")}>
          Effacer
        </button>
      </div>
      {rendu !== null && (
        <div className={`rendu${suffisant ? "" : " manque"}`} aria-live="polite">
          <span className="libelle">{suffisant ? "Rendu monnaie" : "Il manque"}</span>
          <strong>{dh(Math.abs(suffisant ? Math.max(rendu, 0) : rendu))}</strong>
        </div>
      )}
      {valider.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(valider.error)}
        </p>
      )}
      <div className="rangee-boutons">
        <button
          type="button"
          className="bouton bouton-contour"
          onClick={onFermer}
          disabled={valider.isPending}
        >
          Annuler
        </button>
        <button
          type="button"
          className="bouton bouton-plein"
          disabled={!suffisant || valider.isPending}
          onClick={() =>
            recu !== null &&
            valider.mutate(
              { cle, corps: { panier: commande, especesRecues: recu } },
              { onSuccess: onValidee },
            )
          }
        >
          {valider.isPending ? "Validation…" : "Valider la vente"}
        </button>
      </div>
    </Dialogue>
  );
}
