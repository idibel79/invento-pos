import { useState } from "react";
import { dh } from "../ui/format";
import { PageMonCompte } from "./PageMonCompte";
import { useMonCompte } from "./requetes";

/**
 * Bouton « Mon compte · solde » avec la pastille des versements à valider. Il ouvre la page Mon compte,
 * depuis la barre de caisse comme depuis l'écran d'ouverture ({@code page}, qui change le texte du retour).
 */
export function BoutonMonCompte({ classe, page = false }: { classe: string; page?: boolean }) {
  const compte = useMonCompte();
  const [ouverte, setOuverte] = useState(false);
  const aValider = compte.data?.aValider.length ?? 0;
  return (
    <>
      <button
        type="button"
        className={`${classe} bouton-mon-compte`}
        onClick={() => setOuverte(true)}
        aria-label={`Mon compte${aValider > 0 ? `, ${aValider} versement${aValider > 1 ? "s" : ""} à valider` : ""}`}
      >
        Mon compte{compte.data ? ` · ${dh(compte.data.solde)}` : ""}
        {aValider > 0 && <span className="pastille-nombre">{aValider}</span>}
      </button>
      {ouverte && (
        <PageMonCompte
          retour={page ? "Retour à l'ouverture" : "Retour à la caisse"}
          onFermer={() => setOuverte(false)}
        />
      )}
    </>
  );
}
