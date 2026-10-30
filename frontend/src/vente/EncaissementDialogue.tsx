import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { decimal, dh, lireMontant } from "../ui/format";
import { IconeBillet } from "../ui/icones";
import { useValiderVente } from "./requetes";

interface Props {
  commande: Schemas["PanierCommande"];
  total: number;
  onValidee: (vente: Schemas["VenteDto"]) => void;
  onFermer: () => void;
}

/** Montants ronds proposés au-dessus du total (billets courants). */
export function montantsProposes(total: number): number[] {
  const proposes = new Set<number>();
  for (const pas of [10, 50, 100, 200]) {
    const arrondi = Math.ceil(total / pas) * pas;
    if (arrondi > total) proposes.add(arrondi);
  }
  return [...proposes].sort((a, b) => a - b).slice(0, 3);
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
    <Dialogue titre="Encaissement" icone={<IconeBillet />} onFermer={onFermer}>
      <div className="encadre">
        <span className="libelle">Total à payer</span>
        <span className="encadre-montant">{dh(total)}</span>
      </div>
      <div className="choix" role="group" aria-label="Mode de paiement">
        <button type="button" aria-pressed="true">
          Espèces
        </button>
      </div>
      <label className="libelle" htmlFor="montant-recu">
        Montant reçu
      </label>
      <div className="champ-montant">
        <input
          id="montant-recu"
          inputMode="decimal"
          value={saisie}
          onChange={(e) => setSaisie(e.target.value)}
          placeholder={decimal(total)}
          autoFocus
        />
        <span>DH</span>
      </div>
      <div className="choix compact" role="group" aria-label="Montants proposés">
        <button type="button" aria-pressed={recu === total} onClick={() => choisir(total)}>
          Exact
        </button>
        {montantsProposes(total).map((m) => (
          <button key={m} type="button" aria-pressed={recu === m} onClick={() => choisir(m)}>
            {m}
          </button>
        ))}
      </div>
      {rendu !== null && (
        <div className={`rendu${suffisant ? "" : " manque"}`}>
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
