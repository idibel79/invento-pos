import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useSessionCourante } from "../caisse/requetes";
import { Dialogue } from "../ui/Dialogue";
import { decimal, dh, lireMontant, pourcent } from "../ui/format";
import { PaveNumerique, saisirTouche } from "../ui/PaveNumerique";
import { BilletDirham } from "./BilletDirham";
import { useValiderVente } from "./requetes";

/**
 * Espèces dans le tiroir pour rendre la monnaie : fond + ventes en espèces + reçu − versé pendant la
 * session (les tickets TPE ne servent pas à rendre la monnaie) ; null si la session n'est pas connue.
 */
export function especesDisponibles(session: Schemas["SessionCaisseDto"] | null | undefined): number | null {
  if (!session) return null;
  const t = session.tiroir;
  return (
    session.fondInitial +
    (t?.ventesEspeces ?? session.especesEncaissees ?? 0) +
    (t?.recu ?? 0) -
    (t?.verse ?? 0)
  );
}

interface Props {
  commande: Schemas["PanierCommande"];
  total: number;
  /** Aperçu calculé par le serveur, pour le récapitulatif du ticket. */
  apercu?: Schemas["PanierDto"];
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
 * Encaissement en espèces ou par carte (paiement mixte : étape 3), design B « ticket à gauche ».
 * Le rendu affiché aide le caissier ; celui du ticket est calculé par le serveur.
 */
export function EncaissementDialogue({ commande, total, apercu, onValidee, onFermer }: Props) {
  // Une clé par encaissement : un nouvel essai après une coupure ne crée pas une seconde vente.
  const [cle] = useState(() => crypto.randomUUID());
  const [mode, setMode] = useState<Mode>("ESPECES");
  const [saisie, setSaisie] = useState("");
  const valider = useValiderVente();
  const recu = saisie === "" ? null : lireMontant(saisie);
  const rendu = recu === null ? null : recu - total;
  const suffisant = recu !== null && rendu !== null && rendu >= -0.005;
  const carte = mode === "CARTE";
  // Monnaie rendue prise sur les espèces déjà dans le tiroir ; le serveur refait le contrôle.
  const disponibles = especesDisponibles(useSessionCourante().data);
  const monnaieInsuffisante =
    !carte && disponibles !== null && rendu !== null && rendu > 0.005 && rendu > disponibles + 0.005;
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
    <Dialogue titre="Encaissement" onFermer={onFermer} largeur={1040}>
      <div className="encaissement">
        <aside className="encaissement-recap">
          {apercu && (
            <>
              <ul className="recap-lignes">
                {apercu.lignes.map((l) => (
                  <li key={l.produitId}>
                    <span className="recap-designation">
                      {l.quantite} × {l.designation}
                    </span>
                    <span>{decimal(l.montantNet)}</span>
                  </li>
                ))}
              </ul>
              <div className="recap-totaux">
                <span>
                  <span>Prix sans remise</span>
                  <span>{dh(apercu.totaux.sousTotal)}</span>
                </span>
                {apercu.totaux.totalRemises > 0 && (
                  <span>
                    <span>Remises</span>
                    <span>−{dh(apercu.totaux.totalRemises)}</span>
                  </span>
                )}
                <span>
                  <span>Total HT</span>
                  <span>{dh(apercu.totaux.totalHt)}</span>
                </span>
                {apercu.totaux.tva
                  .filter((t) => t.taux > 0)
                  .map((t) => (
                    <span key={t.taux}>
                      <span>TVA {pourcent(t.taux)}</span>
                      <span>{dh(t.totalTva)}</span>
                    </span>
                  ))}
              </div>
            </>
          )}
          <div className="total-a-payer">
            <span>Total TTC à payer</span>
            <strong>{dh(total)}</strong>
          </div>
          <div className="modes-paiement" role="group" aria-label="Mode de paiement">
            <button type="button" aria-pressed={!carte} onClick={() => setMode("ESPECES")}>
              <IconeEspeces />
              Espèces
            </button>
            <button type="button" aria-pressed={carte} onClick={() => setMode("CARTE")}>
              <IconeCarte />
              Carte bancaire
            </button>
          </div>
        </aside>
        <div className="encaissement-saisie">
          {carte ? (
            <div className="encaissement-carte">
              <span className="libelle">Montant à débiter sur le terminal</span>
              <strong>{dh(total)}</strong>
              <p>
                Saisissez ce montant sur le terminal de paiement, puis validez une fois le paiement accepté.
              </p>
            </div>
          ) : (
            <>
              <div className="recu-rendu">
                <div className="champ">
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
                </div>
                <div className="champ">
                  <span className="libelle">
                    {rendu === null || suffisant ? "Rendu monnaie" : "Il manque"}
                  </span>
                  <div
                    className={`rendu${rendu === null ? " vide" : suffisant ? "" : " manque"}`}
                    aria-live="polite"
                  >
                    <strong>
                      {rendu === null ? "–" : dh(Math.abs(suffisant ? Math.max(rendu, 0) : rendu))}
                    </strong>
                  </div>
                </div>
              </div>
              {monnaieInsuffisante && (
                <p className="alerte alerte-danger" role="alert">
                  Monnaie insuffisante dans le tiroir : {dh(Math.max(disponibles ?? 0, 0))} disponibles.
                  Demandez l'appoint ou encaissez par carte.
                </p>
              )}
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
                    <BilletDirham valeur={b} />+{b}
                  </button>
                ))}
              </div>
              <PaveNumerique
                onTouche={(t) => setSaisie((s) => saisirTouche(s, t))}
                onEffacer={() => setSaisie("")}
                onExact={() => setSaisie(enSaisie(total))}
              />
            </>
          )}
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
              disabled={(!carte && (!suffisant || monnaieInsuffisante)) || valider.isPending}
              onClick={envoyer}
            >
              {valider.isPending ? "Validation…" : libelleValider}
            </button>
          </div>
        </div>
      </div>
    </Dialogue>
  );
}

function IconeEspeces() {
  return (
    <svg
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      aria-hidden="true"
    >
      <rect x="2" y="6" width="20" height="12" rx="2" />
      <circle cx="12" cy="12" r="2.5" />
    </svg>
  );
}

function IconeCarte() {
  return (
    <svg
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <rect x="2" y="5" width="20" height="14" rx="2" />
      <path d="M2 10h20M6 15h4" />
    </svg>
  );
}
