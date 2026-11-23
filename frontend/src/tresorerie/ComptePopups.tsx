import { useState } from "react";
import { messageErreur } from "../api/erreurs";
import { useUtilisateur } from "../auth/AuthContext";
import { arrondi, lireComptage } from "../caisse/cloture";
import { useParametres } from "../caisse/requetes";
import { Dialogue } from "../ui/Dialogue";
import { decimal, dh, lireMontant } from "../ui/format";
import { nouvelleCle, useMonCompte, useVerserALaPrincipale } from "./requetes";
import { VersementAValider } from "./VersementAValider";

/** Montant saisi avec son plafond ; signale un dépassement (le serveur refait la vérification et fait foi). */
function ChampPlafonne({
  id,
  libelle,
  plafond,
  saisie,
  onSaisie,
  autoFocus,
}: {
  id: string;
  libelle: string;
  plafond: number;
  saisie: string;
  onSaisie: (valeur: string) => void;
  autoFocus?: boolean;
}) {
  const montant = saisie.trim() === "" ? 0 : lireMontant(saisie);
  return (
    <>
      <label className="champ" htmlFor={id}>
        <span className="libelle">
          {libelle} · max <strong>{dh(plafond)}</strong>
        </span>
      </label>
      <div className="champ-montant">
        <input
          id={id}
          inputMode="decimal"
          autoComplete="off"
          placeholder={decimal(0)}
          value={saisie}
          onChange={(e) => onSaisie(e.target.value)}
          autoFocus={autoFocus}
        />
        <span>DH</span>
      </div>
      {montant !== null && montant > plafond && (
        <p className="erreur-champ">
          {libelle} : au plus {dh(plafond)}.
        </p>
      )}
    </>
  );
}

/**
 * Popup centrée : versement à la caisse principale, en espèces et en tickets TPE (comme le paiement d'un ticket).
 * Caisse ouverte, l'argent vient du tiroir (ventes du jour comprises) ou de ce que le caissier garde sur lui, chaque
 * source dans son plafond. Un champ vide vaut 0.
 */
export function VerserDialogue({ onFermer }: { onFermer: () => void }) {
  const utilisateur = useUtilisateur();
  const compte = useMonCompte();
  const verser = useVerserALaPrincipale();
  const [tiroirEspeces, setTiroirEspeces] = useState("");
  const [tiroirTpe, setTiroirTpe] = useState("");
  const [compteEspeces, setCompteEspeces] = useState("");
  const [compteTpe, setCompteTpe] = useState("");
  const [cle] = useState(nouvelleCle);
  const enSession = compte.data?.sessionOuverte ?? false;
  const tiroir = enSession ? lireComptage(tiroirEspeces, tiroirTpe) : null;
  const surMoi = lireComptage(compteEspeces, compteTpe);
  const saisieInvalide =
    (enSession && tiroir === null && (tiroirEspeces.trim() !== "" || tiroirTpe.trim() !== "")) ||
    (surMoi === null && (compteEspeces.trim() !== "" || compteTpe.trim() !== ""));
  const parts = {
    tiroirEspeces: tiroir?.especes ?? 0,
    tiroirTpe: tiroir?.tpe ?? 0,
    compteEspeces: surMoi?.especes ?? 0,
    compteTpe: surMoi?.tpe ?? 0,
  };
  const montant = arrondi(parts.tiroirEspeces + parts.tiroirTpe + parts.compteEspeces + parts.compteTpe);
  const plafonds = {
    tiroirEspeces: compte.data?.plafondTiroirEspeces ?? 0,
    tiroirTpe: compte.data?.plafondTiroirTpe ?? 0,
    compteEspeces: compte.data?.plafondCompteEspeces ?? 0,
    compteTpe: compte.data?.plafondCompteTpe ?? 0,
  };
  const depasse = (Object.keys(parts) as (keyof typeof parts)[]).some((k) => parts[k] > plafonds[k]);
  const valide = !saisieInvalide && montant > 0 && !depasse;

  return (
    <Dialogue
      titre="Verser à la caisse principale"
      sousTitre={`Solde dû : ${dh(compte.data?.solde ?? 0)}`}
      onFermer={onFermer}
    >
      {enSession && (
        <>
          <p className="texte-secondaire">
            <strong>Depuis le tiroir</strong> (fond et ventes du jour compris)
          </p>
          <ChampPlafonne
            id="versement-tiroir-especes"
            libelle="Espèces du tiroir"
            plafond={plafonds.tiroirEspeces}
            saisie={tiroirEspeces}
            onSaisie={setTiroirEspeces}
            autoFocus
          />
          <ChampPlafonne
            id="versement-tiroir-tpe"
            libelle="Tickets TPE du tiroir"
            plafond={plafonds.tiroirTpe}
            saisie={tiroirTpe}
            onSaisie={setTiroirTpe}
          />
          <p className="texte-secondaire">
            <strong>Depuis mon compte</strong> (argent gardé sur moi, hors tiroir)
          </p>
        </>
      )}
      <ChampPlafonne
        id="versement-especes"
        libelle={enSession ? "Espèces hors tiroir" : "Espèces"}
        plafond={plafonds.compteEspeces}
        saisie={compteEspeces}
        onSaisie={setCompteEspeces}
        autoFocus={!enSession}
      />
      <ChampPlafonne
        id="versement-tpe"
        libelle={enSession ? "Tickets TPE hors tiroir" : "Tickets TPE"}
        plafond={plafonds.compteTpe}
        saisie={compteTpe}
        onSaisie={setCompteTpe}
      />
      <p className="texte-secondaire">
        {utilisateur.role === "ADMIN"
          ? "Vous tenez la caisse principale : votre versement est auto-validé."
          : "Le versement reste en transit jusqu'à ce que l'admin confirme l'avoir reçu."}
      </p>
      {verser.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(verser.error)}
        </p>
      )}
      <div className="rangee-boutons">
        <button type="button" className="bouton bouton-contour" onClick={onFermer}>
          Annuler
        </button>
        <button
          type="button"
          className="bouton bouton-plein"
          disabled={!valide || verser.isPending}
          onClick={() =>
            verser.mutate(
              {
                montantEspeces: arrondi(parts.tiroirEspeces + parts.compteEspeces),
                montantTpe: arrondi(parts.tiroirTpe + parts.compteTpe),
                tiroirEspeces: parts.tiroirEspeces,
                tiroirTpe: parts.tiroirTpe,
                cle,
              },
              { onSuccess: onFermer },
            )
          }
        >
          Verser {montant > 0 ? dh(montant) : ""}
        </button>
      </div>
    </Dialogue>
  );
}

/** Popup centrée : versements reçus de la caisse principale, à confirmer ou refuser. */
export function AValiderDialogue({ onFermer }: { onFermer: () => void }) {
  const compte = useMonCompte();
  const fuseau = useParametres().data?.fuseauHoraire;
  const liste = compte.data?.aValider ?? [];

  return (
    <Dialogue titre="Versements à valider" onFermer={onFermer} largeur={520}>
      {compte.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(compte.error)}
        </p>
      )}
      {compte.isSuccess && liste.length === 0 ? (
        <p className="vide">Aucun versement à valider.</p>
      ) : (
        <ul className="liste-a-valider">
          {liste.map((v) => (
            <VersementAValider
              key={v.id}
              versement={v}
              emetteur="Caisse principale"
              fuseau={fuseau}
              caisseOuverte={compte.data?.sessionOuverte ?? false}
            />
          ))}
        </ul>
      )}
      <div className="rangee-boutons">
        <button type="button" className="bouton bouton-contour" onClick={onFermer}>
          Fermer
        </button>
      </div>
    </Dialogue>
  );
}
