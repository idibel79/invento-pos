import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { dh, lireMontant } from "../ui/format";
import { PaveNumerique, saisirTouche } from "../ui/PaveNumerique";
import { BilletDirham } from "./BilletDirham";
import { useValiderVente } from "./requetes";

interface Props {
  commande: Schemas["PanierCommande"];
  total: number;
  onValidee: (vente: Schemas["VenteDto"]) => void;
  onFermer: () => void;
}

type Mode = NonNullable<Schemas["ValidationCommande"]["mode"]>;

/** Billets en circulation : chaque clic ajoute le billet au montant reçu. */
export const BILLETS = [20, 50, 100, 200] as const;

/** Montant reçu après l'ajout d'un billet (un montant vide compte pour zéro). */
export function ajouterBillet(recu: number | null, billet: number): number {
  return Math.round(((recu ?? 0) + billet) * 100) / 100;
}

/** Montant écrit dans le champ, sans zéros inutiles, pour que le pavé puisse le compléter. */
function enSaisie(montant: number): string {
  return String(montant).replace(".", ",");
}

/**
 * Encaissement en espèces ou par carte (paiement mixte : étape 3). Le rendu affiché aide le
 * caissier ; celui du ticket est calculé par le serveur.
 */
export function EncaissementDialogue({ commande, total, onValidee, onFermer }: Props) {
  // Une clé par encaissement : un nouvel essai après une coupure ne crée pas une seconde vente.
  const [cle] = useState(() => crypto.randomUUID());
  const [mode, setMode] = useState<Mode>("ESPECES");
  const [saisie, setSaisie] = useState("");
  const valider = useValiderVente();
  const recu = saisie === "" ? null : lireMontant(saisie);
  const rendu = recu === null ? null : recu - total;
  const suffisant = recu !== null && rendu !== null && rendu >= -0.005;
  const carte = mode === "CARTE";
  const libelleValider = carte
    ? "Valider le paiement par carte"
    : suffisant && rendu > 0.005
      ? `Valider · rendre ${dh(rendu)}`
      : "Valider la vente";

  function envoyer() {
    const corps: Schemas["ValidationCommande"] = carte
      ? { panier: commande, mode: "CARTE" }
      : { panier: commande, mode: "ESPECES", especesRecues: recu };
    valider.mutate({ cle, corps }, { onSuccess: onValidee });
  }

  return (
    <Dialogue titre="Encaissement" onFermer={onFermer} largeur={780}>
      <div className="modes-paiement" role="group" aria-label="Mode de paiement">
        <button type="button" aria-pressed={!carte} onClick={() => setMode("ESPECES")}>
          Espèces
        </button>
        <button type="button" aria-pressed={carte} onClick={() => setMode("CARTE")}>
          Carte bancaire
        </button>
      </div>
      <div className={`encaissement${carte ? " encaissement-carte" : ""}`}>
        <div className="encaissement-saisie">
          <div className="total-a-payer">
            <span>Total à payer</span>
            <strong>{dh(total)}</strong>
          </div>
          {carte ? (
            <p className="encaissement-consigne">
              Saisissez {dh(total)} sur le terminal de paiement, puis validez une fois le paiement accepté.
            </p>
          ) : (
            <>
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
                  placeholder={enSaisie(total)}
                  autoFocus
                />
                <span>DH</span>
              </div>
              <div
                className={`rendu${rendu === null ? " vide" : suffisant ? "" : " manque"}`}
                aria-live="polite"
              >
                <span className="libelle">{rendu === null || suffisant ? "Rendu monnaie" : "Il manque"}</span>
                <strong>{rendu === null ? "–" : dh(Math.abs(suffisant ? Math.max(rendu, 0) : rendu))}</strong>
              </div>
              <span className="libelle">Ajouter un billet</span>
              <div className="billets" role="group" aria-label="Billets reçus">
                {BILLETS.map((b) => (
                  <button
                    key={b}
                    type="button"
                    aria-label={`Ajouter un billet de ${b} DH`}
                    onClick={() =>
                      setSaisie((s) => enSaisie(ajouterBillet(s === "" ? null : lireMontant(s), b)))
                    }
                  >
                    <BilletDirham valeur={b} />
                    {b}
                  </button>
                ))}
              </div>
            </>
          )}
        </div>
        {!carte && (
          <PaveNumerique
            onTouche={(t) => setSaisie((s) => saisirTouche(s, t))}
            onEffacer={() => setSaisie("")}
            onExact={() => setSaisie(enSaisie(total))}
          />
        )}
      </div>
      {valider.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(valider.error)}
        </p>
      )}
      <div className="rangee-encaissement">
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
          disabled={(!carte && !suffisant) || valider.isPending}
          onClick={envoyer}
        >
          {valider.isPending ? "Validation…" : libelleValider}
        </button>
      </div>
    </Dialogue>
  );
}
