import { useState } from "react";
import { messageErreur } from "../api/erreurs";
import { useUtilisateur } from "../auth/AuthContext";
import { lireComptage } from "../caisse/cloture";
import { useParametres } from "../caisse/requetes";
import { Dialogue } from "../ui/Dialogue";
import { decimal, dh } from "../ui/format";
import { nouvelleCle, useMonCompte, useVerserALaPrincipale } from "./requetes";
import { VersementAValider } from "./VersementAValider";

/**
 * Popup centrée : versement à la caisse principale, en espèces et en tickets TPE (comme le paiement d'un ticket),
 * dans la limite du solde. Un champ vide vaut 0.
 */
export function VerserDialogue({ onFermer }: { onFermer: () => void }) {
  const utilisateur = useUtilisateur();
  const compte = useMonCompte();
  const verser = useVerserALaPrincipale();
  const [saisieEspeces, setSaisieEspeces] = useState("");
  const [saisieTpe, setSaisieTpe] = useState("");
  const [cle] = useState(nouvelleCle);
  const repartition = lireComptage(saisieEspeces, saisieTpe);
  const montant = repartition?.total ?? null;
  const solde = compte.data?.solde ?? 0;
  const duEspeces = Math.max(compte.data?.soldeEspeces ?? 0, 0);
  const duTpe = compte.data?.soldeTpe ?? 0;
  // Le serveur refait la vérification et fait foi.
  const depasseEspeces = repartition !== null && repartition.especes > duEspeces;
  const depasseTpe = repartition !== null && repartition.tpe > duTpe;
  const valide = montant !== null && montant > 0 && !depasseEspeces && !depasseTpe;

  return (
    <Dialogue titre="Verser à la caisse principale" sousTitre={`Solde dû : ${dh(solde)}`} onFermer={onFermer}>
      <label className="champ" htmlFor="versement-especes">
        <span className="libelle">
          Espèces · dû <strong>{dh(duEspeces)}</strong>
        </span>
      </label>
      <div className="champ-montant">
        <input
          id="versement-especes"
          inputMode="decimal"
          autoComplete="off"
          placeholder={decimal(0)}
          value={saisieEspeces}
          onChange={(e) => setSaisieEspeces(e.target.value)}
          autoFocus
        />
        <span>DH</span>
      </div>
      <label className="champ" htmlFor="versement-tpe">
        <span className="libelle">
          Tickets TPE · dû <strong>{dh(duTpe)}</strong>
        </span>
      </label>
      <div className="champ-montant">
        <input
          id="versement-tpe"
          inputMode="decimal"
          autoComplete="off"
          placeholder={decimal(0)}
          value={saisieTpe}
          onChange={(e) => setSaisieTpe(e.target.value)}
        />
        <span>DH</span>
      </div>
      {depasseEspeces && (
        <p className="erreur-champ">Les espèces dépassent votre dû en espèces ({dh(duEspeces)}).</p>
      )}
      {depasseTpe && <p className="erreur-champ">Les tickets TPE dépassent votre dû en TPE ({dh(duTpe)}).</p>}
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
            repartition !== null &&
            verser.mutate(
              { montantEspeces: repartition.especes, montantTpe: repartition.tpe, cle },
              { onSuccess: onFermer },
            )
          }
        >
          Verser {montant !== null && montant > 0 ? dh(montant) : ""}
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
            <VersementAValider key={v.id} versement={v} emetteur="Caisse principale" fuseau={fuseau} />
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
