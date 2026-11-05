import { useState } from "react";
import { messageErreur } from "../api/erreurs";
import { useUtilisateur } from "../auth/AuthContext";
import { useParametres } from "../caisse/requetes";
import { Dialogue } from "../ui/Dialogue";
import { decimal, dh, lireMontant } from "../ui/format";
import { nouvelleCle, useMonCompte, useVerserALaPrincipale } from "./requetes";
import { VersementAValider } from "./VersementAValider";

/** Popup centrée : versement à la caisse principale, dans la limite du solde. */
export function VerserDialogue({ onFermer }: { onFermer: () => void }) {
  const utilisateur = useUtilisateur();
  const compte = useMonCompte();
  const verser = useVerserALaPrincipale();
  const [saisie, setSaisie] = useState("");
  const [cle] = useState(nouvelleCle);
  const montant = lireMontant(saisie);
  const solde = compte.data?.solde ?? 0;
  const depasse = montant !== null && montant > solde;
  const valide = montant !== null && montant > 0 && !depasse;

  return (
    <Dialogue titre="Verser à la caisse principale" sousTitre={`Solde dû : ${dh(solde)}`} onFermer={onFermer}>
      <label className="champ" htmlFor="montant-a-verser">
        <span className="libelle">Montant versé</span>
      </label>
      <div className="champ-montant">
        <input
          id="montant-a-verser"
          inputMode="decimal"
          autoComplete="off"
          placeholder={decimal(0)}
          value={saisie}
          onChange={(e) => setSaisie(e.target.value)}
          autoFocus
        />
        <span>DH</span>
      </div>
      {depasse && <p className="erreur-champ">Le montant dépasse votre solde ({dh(solde)}).</p>}
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
          onClick={() => montant !== null && verser.mutate({ montant, cle }, { onSuccess: onFermer })}
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
