import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useSessionCourante } from "../caisse/requetes";
import { ChoixClient } from "../client/ChoixClient";
import type { Client } from "../client/requetes";
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

/**
 * MIXTE : une part en espèces, le reste par carte (envoyé au serveur en mode CARTE avec les espèces).
 * CREDIT : la totalité du ticket va sur la dette d'un client de l'annuaire.
 */
type Mode = NonNullable<Schemas["ValidationCommande"]["mode"]> | "MIXTE";

/** Billets en circulation : un clic remplace le montant reçu par la valeur du billet (sans cumul). */
export const BILLETS = [20, 50, 100, 200] as const;

/** Montant écrit dans le champ, sans zéros inutiles, pour que le pavé puisse le compléter. */
function enSaisie(montant: number): string {
  return String(montant).replace(".", ",");
}

/**
 * Encaissement en espèces, par carte, les deux (une part en espèces, le reste par carte) ou à crédit (client de
 * l'annuaire), design B « ticket à gauche ». Un client peut aussi être choisi pour une vente payée comptant : son
 * nom est imprimé sur le ticket. Le rendu affiché aide le caissier ; celui du ticket est calculé par le serveur.
 */
export function EncaissementDialogue({ commande, total, apercu, onValidee, onFermer }: Props) {
  // Une clé par encaissement : un nouvel essai après une coupure ne crée pas une seconde vente.
  const [cle] = useState(() => crypto.randomUUID());
  const [mode, setMode] = useState<Mode>("ESPECES");
  const [saisie, setSaisie] = useState("");
  // Carte : la vente n'est validée qu'une fois le paiement accepté sur le terminal.
  const [confirmerCarte, setConfirmerCarte] = useState(false);
  const [client, setClient] = useState<Client | null>(null);
  const [choisirClient, setChoisirClient] = useState(false);
  const valider = useValiderVente();
  const recu = saisie === "" ? null : lireMontant(saisie);
  const rendu = recu === null ? null : recu - total;
  const suffisant = recu !== null && rendu !== null && rendu >= -0.005;
  const carte = mode === "CARTE";
  const mixte = mode === "MIXTE";
  const credit = mode === "CREDIT";
  // Paiement mixte : la part en espèces est réglée sans monnaie, le terminal débite le reste.
  const resteCarte = total - (recu ?? 0);
  const mixteValide = recu !== null && !Number.isNaN(recu) && recu > 0.005 && resteCarte > 0.005;
  // Monnaie rendue prise sur les espèces déjà dans le tiroir ; le serveur refait le contrôle.
  const disponibles = especesDisponibles(useSessionCourante().data);
  const monnaieInsuffisante =
    mode === "ESPECES" &&
    disponibles !== null &&
    rendu !== null &&
    rendu > 0.005 &&
    rendu > disponibles + 0.005;
  const libelleValider = credit
    ? client
      ? `Valider à crédit · ${client.nom}`
      : "Choisissez le client"
    : carte
      ? "Valider le paiement par carte"
      : mixte
        ? mixteValide
          ? `Valider · ${dh(resteCarte)} par carte`
          : "Valider le paiement mixte"
        : suffisant && rendu > 0.005
          ? `Valider · rendre ${dh(rendu)}`
          : "Valider la vente";
  // Plafond de crédit du client : le serveur refait le contrôle.
  const plafondDepasse = credit && client?.plafond != null && client.dette + total > client.plafond + 0.005;
  const validable = credit
    ? client !== null && !plafondDepasse
    : carte || (mixte ? mixteValide : suffisant && !monnaieInsuffisante);

  function choisirBillet(billet: number) {
    setSaisie(enSaisie(billet));
  }

  function choisirMode(nouveau: Mode) {
    setMode(nouveau);
    if (nouveau !== mode) setSaisie("");
  }

  function envoyer() {
    // Le mode CARTE avec une part en espèces est un paiement mixte pour le serveur.
    const clientId = client?.id ?? null;
    const corps: Schemas["ValidationCommande"] = credit
      ? { panier: commande, mode: "CREDIT", clientId }
      : carte
        ? { panier: commande, mode: "CARTE", clientId }
        : mixte
          ? { panier: commande, mode: "CARTE", especesRecues: recu, clientId }
          : { panier: commande, mode: "ESPECES", especesRecues: recu, clientId };
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
            <button type="button" aria-pressed={mode === "ESPECES"} onClick={() => choisirMode("ESPECES")}>
              <IconeEspeces />
              Espèces
            </button>
            <button type="button" aria-pressed={carte} onClick={() => choisirMode("CARTE")}>
              <IconeCarte />
              Carte bancaire
            </button>
            <button type="button" aria-pressed={mixte} onClick={() => choisirMode("MIXTE")}>
              <IconeMixte />
              Espèces + carte
            </button>
            <button type="button" aria-pressed={credit} onClick={() => choisirMode("CREDIT")}>
              <IconeCredit />
              Crédit
            </button>
          </div>
          {!credit && (
            <div className="encaissement-client">
              <span className="texte-secondaire">Client</span>
              {client ? (
                <>
                  <strong>{client.nom}</strong>
                  <button
                    type="button"
                    className="bouton-icone"
                    aria-label="Retirer le client"
                    title="Retirer le client"
                    onClick={() => setClient(null)}
                  >
                    ×
                  </button>
                </>
              ) : (
                <button type="button" className="lien-action" onClick={() => setChoisirClient(true)}>
                  Ajouter un client (facultatif)
                </button>
              )}
            </div>
          )}
        </aside>
        <div className="encaissement-saisie">
          {credit ? (
            <div className="encaissement-credit">
              <h3>Client qui paie à crédit</h3>
              <ChoixClient choisi={client} onChoisir={setClient} autoFocus />
              {client && (
                <dl className="dette-apres">
                  <div>
                    <dt>{client.dette < -0.005 ? "Avance actuelle" : "Dette actuelle"}</dt>
                    <dd>{dh(Math.abs(client.dette))}</dd>
                  </div>
                  <div>
                    <dt>Cette vente</dt>
                    <dd>+ {dh(total)}</dd>
                  </div>
                  <div className="dette-apres-total">
                    <dt>
                      {client.dette + total < -0.005 ? "Avance après la vente" : "Dette après la vente"}
                    </dt>
                    <dd>{dh(Math.abs(client.dette + total))}</dd>
                  </div>
                  {client.plafond != null && (
                    <div>
                      <dt>Plafond de crédit</dt>
                      <dd>{dh(client.plafond)}</dd>
                    </div>
                  )}
                </dl>
              )}
              {plafondDepasse && client?.plafond != null && (
                <p className="alerte alerte-danger" role="alert">
                  Plafond de crédit dépassé : {client.nom} ne peut pas devoir plus de {dh(client.plafond)}.
                  Encaissez autrement ou une partie de la dette d'abord.
                </p>
              )}
            </div>
          ) : mixte ? (
            <>
              <div className="recu-rendu">
                <div className="champ">
                  <label className="libelle" htmlFor="montant-especes">
                    Payé en espèces
                  </label>
                  <div className="champ-montant">
                    <input
                      id="montant-especes"
                      inputMode="decimal"
                      autoComplete="off"
                      value={saisie}
                      onChange={(e) => setSaisie(e.target.value)}
                      placeholder="0"
                      autoFocus
                    />
                    <span>DH</span>
                  </div>
                </div>
                <div className="champ">
                  <span className="libelle">Reste par carte</span>
                  <div
                    className={`rendu${recu === null ? " vide" : mixteValide ? "" : " manque"}`}
                    aria-live="polite"
                  >
                    <strong>{recu === null ? "–" : dh(Math.max(resteCarte, 0))}</strong>
                  </div>
                </div>
              </div>
              {recu !== null && !mixteValide && (
                <p className="alerte alerte-danger" role="alert">
                  La part en espèces doit être entre 0 et {dh(total)} : le reste est payé par carte.
                </p>
              )}
              {mixteValide && (
                <p className="encaissement-aide">
                  Encaissez {dh(recu ?? 0)} en espèces, puis saisissez {dh(resteCarte)} sur le terminal de
                  paiement et validez une fois le paiement accepté.
                </p>
              )}
              <Billets onBillet={choisirBillet} />
              <PaveNumerique
                onTouche={(t) => setSaisie((s) => saisirTouche(s, t))}
                onEffacer={() => setSaisie("")}
                onExact={() => choisirMode("CARTE")}
                libelleExact="Tout par carte"
              />
            </>
          ) : carte ? (
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
              <Billets onBillet={choisirBillet} />
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
              disabled={!validable || valider.isPending}
              onClick={() => (carte || mixte ? setConfirmerCarte(true) : envoyer())}
            >
              {valider.isPending ? "Validation…" : libelleValider}
            </button>
          </div>
        </div>
      </div>
      {choisirClient && (
        <Dialogue
          titre="Client de la vente"
          sousTitre="Son nom sera imprimé sur le ticket"
          onFermer={() => setChoisirClient(false)}
          largeur={560}
        >
          <ChoixClient
            choisi={client}
            autoFocus
            onChoisir={(c) => {
              setClient(c);
              setChoisirClient(false);
            }}
          />
          <div className="rangee-boutons">
            <button type="button" className="bouton bouton-contour" onClick={() => setChoisirClient(false)}>
              Annuler
            </button>
          </div>
        </Dialogue>
      )}
      {confirmerCarte && (
        <Dialogue titre="Paiement par carte" onFermer={() => setConfirmerCarte(false)}>
          <p>
            Le terminal a-t-il accepté le paiement de <strong>{dh(carte ? total : resteCarte)}</strong> ?
          </p>
          <div className="rangee-boutons">
            <button type="button" className="bouton bouton-contour" onClick={() => setConfirmerCarte(false)}>
              Non, revenir
            </button>
            <button
              type="button"
              className="bouton bouton-plein"
              autoFocus
              onClick={() => {
                setConfirmerCarte(false);
                envoyer();
              }}
            >
              Oui, valider la vente
            </button>
          </div>
        </Dialogue>
      )}
    </Dialogue>
  );
}

/** Billets en circulation : un clic met le montant en espèces à la valeur du billet. */
function Billets({ onBillet }: { onBillet: (billet: number) => void }) {
  return (
    <div className="billets" role="group" aria-label="Billets reçus">
      {BILLETS.map((b) => (
        <button key={b} type="button" aria-label={`Billet de ${b} DH`} onClick={() => onBillet(b)}>
          <BilletDirham valeur={b} />
          {b}
        </button>
      ))}
    </div>
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

function IconeMixte() {
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
      <rect x="2" y="4" width="13" height="9" rx="1.5" />
      <circle cx="8.5" cy="8.5" r="1.8" />
      <rect x="9" y="11" width="13" height="9" rx="1.5" />
      <path d="M9 14h13" />
    </svg>
  );
}

function IconeCredit() {
  return (
    <svg
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M4 4h16v16H4z" />
      <path d="M8 9h8M8 13h5" />
      <circle cx="16" cy="16" r="0.5" />
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
