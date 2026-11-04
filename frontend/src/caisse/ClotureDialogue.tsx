import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { decimal, dh, heure, lireMontant } from "../ui/format";
import { IconeCaisse } from "../ui/icones";
import { cles, useCloturerSession, useParametres } from "./requetes";

/** Évite les « −0,00 » et les restes binaires des soustractions de décimaux. */
function arrondi(valeur: number): number {
  return Math.round(valeur * 100) / 100 || 0;
}

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
  const parametres = useParametres();
  const montant = lireMontant(saisie);
  const resultat = cloturer.data;
  const especes = session.especesEncaissees ?? 0;
  const attendues = session.fondInitial + especes;
  const ecartPrevu = montant === null ? null : arrondi(montant - attendues);

  if (resultat) {
    const ecart = resultat.ecart ?? 0;
    return (
      <Dialogue
        titre="Caisse clôturée"
        icone={<IconeCaisse />}
        ton="succes"
        onFermer={() => terminer()}
        largeur={560}
        classe="cloture"
      >
        <div className="encadre-gris">
          <div className="encadre-ligne">
            <span>Fond de caisse</span>
            <span>{dh(resultat.fondInitial)}</span>
          </div>
          <div className="encadre-ligne">
            <span>Espèces encaissées</span>
            <span>{dh(resultat.especesEncaissees ?? 0)}</span>
          </div>
          <div className="encadre-ligne encadre-total">
            <span>Espèces attendues</span>
            <span>{dh(resultat.fondInitial + (resultat.especesEncaissees ?? 0))}</span>
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
    <Dialogue
      titre="Clôturer la caisse"
      sousTitre={`${session.caisseNom} · ouverte à ${heure(session.ouverture, parametres.data?.fuseauHoraire)}`}
      icone={<IconeCaisse />}
      onFermer={onFermer}
      largeur={560}
      classe="cloture"
    >
      <div className="encadre-gris">
        <div className="encadre-ligne">
          <span>Fond de caisse</span>
          <span>{dh(session.fondInitial)}</span>
        </div>
        <div className="encadre-ligne">
          <span>Espèces encaissées</span>
          <span>{dh(especes)}</span>
        </div>
        <div className="encadre-ligne encadre-total">
          <span>Espèces attendues</span>
          <span>{dh(attendues)}</span>
        </div>
      </div>
      <label className="champ" htmlFor="montant-compte">
        <span className="libelle">Espèces comptées dans le tiroir</span>
      </label>
      <div className="champ-montant">
        <input
          id="montant-compte"
          inputMode="decimal"
          autoComplete="off"
          placeholder={decimal(0)}
          value={saisie}
          onChange={(e) => setSaisie(e.target.value)}
          autoFocus
        />
        <span>DH</span>
      </div>
      {/* Aperçu : l'écart officiel est recalculé par le serveur à la clôture. */}
      <div className={`rendu${ecartPrevu === null ? " vide" : ecartPrevu < 0 ? " manque" : ""}`}>
        <span className="libelle">Écart</span>
        <strong>{ecartPrevu === null ? "—" : `${ecartPrevu > 0 ? "+" : ""}${dh(ecartPrevu)}`}</strong>
      </div>
      {nombreEnAttente > 0 && (
        <p className="alerte alerte-danger" role="alert">
          {nombreEnAttente} vente{nombreEnAttente > 1 ? "s" : ""} en attente{" "}
          {nombreEnAttente > 1 ? "seront annulées" : "sera annulée"} et leur stock libéré.
        </p>
      )}
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
          className="bouton bouton-plein danger"
          disabled={montant === null || cloturer.isPending}
          onClick={() => montant !== null && cloturer.mutate({ id: session.id, montantCompte: montant })}
        >
          Clôturer la caisse
        </button>
      </div>
    </Dialogue>
  );
}
