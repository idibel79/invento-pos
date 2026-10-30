import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { decimal, dh, lireMontant } from "../ui/format";
import { IconeCaisse } from "../ui/icones";
import { cles, useCloturerSession } from "./requetes";

interface Props {
  session: Schemas["SessionCaisseDto"];
  nombreEnAttente: number;
  onFermer: () => void;
}

/** Clôture : comptage des espèces et écart. Les ventes en attente sont annulées par le serveur. */
export function ClotureDialogue({ session, nombreEnAttente, onFermer }: Props) {
  const queryClient = useQueryClient();
  const cloturer = useCloturerSession();
  const [saisie, setSaisie] = useState("");
  const montant = lireMontant(saisie);
  const resultat = cloturer.data;

  if (resultat) {
    const ecart = resultat.ecart ?? 0;
    return (
      <Dialogue titre="Caisse clôturée" icone={<IconeCaisse />} ton="succes" onFermer={() => terminer()}>
        <div className="encadre-gris">
          <div className="encadre-ligne">
            <span>Fond de caisse</span>
            <span>{dh(resultat.fondInitial)}</span>
          </div>
          <div className="encadre-ligne">
            <span>Espèces encaissées</span>
            <span>{dh(resultat.especesEncaissees ?? 0)}</span>
          </div>
          <div className="encadre-ligne">
            <span>Montant compté</span>
            <span>{dh(resultat.montantCompte ?? 0)}</span>
          </div>
        </div>
        <div className={`rendu${ecart < 0 ? " manque" : ""}`}>
          <span className="libelle">Écart</span>
          <strong>
            {ecart > 0 ? "+" : ""}
            {dh(ecart)}
          </strong>
        </div>
        <div className="rangee-boutons">
          <button type="button" className="bouton bouton-plein" onClick={() => terminer()} autoFocus>
            Terminer
          </button>
        </div>
      </Dialogue>
    );
  }

  function terminer() {
    queryClient.setQueryData(cles.sessionCourante, null);
    onFermer();
  }

  return (
    <Dialogue titre="Clôturer la caisse" icone={<IconeCaisse />} onFermer={onFermer}>
      <p className="texte-secondaire">
        {session.caisseNom} · fond de caisse {dh(session.fondInitial)}. Comptez les espèces du tiroir.
      </p>
      {nombreEnAttente > 0 && (
        <p className="alerte alerte-danger" role="alert">
          {nombreEnAttente} vente(s) en attente seront annulées et leur stock libéré.
        </p>
      )}
      <label className="champ" htmlFor="montant-compte">
        <span className="libelle">Espèces comptées</span>
      </label>
      <div className="champ-montant">
        <input
          id="montant-compte"
          inputMode="decimal"
          placeholder={decimal(0)}
          value={saisie}
          onChange={(e) => setSaisie(e.target.value)}
          autoFocus
        />
        <span>DH</span>
      </div>
      {cloturer.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(cloturer.error)}
        </p>
      )}
      <div className="rangee-boutons">
        <button type="button" className="bouton bouton-contour" onClick={onFermer}>
          Annuler
        </button>
        <button
          type="button"
          className="bouton bouton-plein"
          disabled={montant === null || cloturer.isPending}
          onClick={() => montant !== null && cloturer.mutate({ id: session.id, montantCompte: montant })}
        >
          Clôturer
        </button>
      </div>
    </Dialogue>
  );
}
