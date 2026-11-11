import { useState } from "react";
import { messageErreur } from "../api/erreurs";
import { useParametres } from "../caisse/requetes";
import { decimal } from "../ui/format";
import { EncaisserDetteDialogue } from "../client/EncaisserDetteDialogue";
import { AValiderDialogue, VerserDialogue } from "./ComptePopups";
import { JournalTresorerie } from "./JournalTresorerie";
import { useMonCompte } from "./requetes";

/**
 * Rubrique Mon compte de l'administration, présentée comme la Caisse principale : solde dû et actions en
 * tête, puis le journal de mes versements, alimentations et ventes.
 */
export function MonCompte() {
  const fuseau = useParametres().data?.fuseauHoraire;
  const [popup, setPopup] = useState<"verser" | "valider" | "dette" | null>(null);
  const compte = useMonCompte();
  const aValider = compte.data?.aValider.length ?? 0;

  return (
    <div className="mon-compte-ecran">
      <div className="tresorerie-entete">
        <div className="tresorerie-solde">
          <span>Solde dû à la caisse principale</span>
          <strong className="tresorerie-solde-montant">
            {compte.data ? decimal(compte.data.solde) : "…"} <span>DH</span>
          </strong>
        </div>
        <button type="button" className="bouton-compact" onClick={() => setPopup("valider")}>
          À valider ({aValider})
        </button>
        <button type="button" className="bouton-compact" onClick={() => setPopup("dette")}>
          Encaisser une dette
        </button>
        <button type="button" className="bouton-compact plein" onClick={() => setPopup("verser")}>
          Verser
        </button>
      </div>
      {compte.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(compte.error)}
        </p>
      )}
      <JournalTresorerie monCompte fuseau={fuseau} />
      {popup === "verser" && <VerserDialogue onFermer={() => setPopup(null)} />}
      {popup === "valider" && <AValiderDialogue onFermer={() => setPopup(null)} />}
      {popup === "dette" && <EncaisserDetteDialogue onFermer={() => setPopup(null)} />}
    </div>
  );
}
