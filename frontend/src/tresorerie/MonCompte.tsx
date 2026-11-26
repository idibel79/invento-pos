import { useState } from "react";
import { messageErreur } from "../api/erreurs";
import { arrondi } from "../caisse/cloture";
import { useParametres, useSessionCourante } from "../caisse/requetes";
import { decimal, dh } from "../ui/format";
import { EncaisserDetteDialogue } from "../client/EncaisserDetteDialogue";
import { AValiderDialogue, VerserDialogue } from "./ComptePopups";
import { JournalTresorerie } from "./JournalTresorerie";
import { RepartitionDu } from "./RepartitionDu";
import { TiroirEtCompte } from "./TiroirEtCompte";
import { useMonCompte } from "./requetes";

/**
 * Rubrique Mon compte de l'administration, présentée comme la Caisse principale : solde dû et actions en
 * tête, puis le journal de mes versements, alimentations et ventes.
 */
export function MonCompte() {
  const fuseau = useParametres().data?.fuseauHoraire;
  const [popup, setPopup] = useState<"verser" | "valider" | "dette" | null>(null);
  const compte = useMonCompte();
  const session = useSessionCourante().data;
  const aValider = compte.data?.aValider.length ?? 0;
  // Caisse ouverte : le tableau compte les ventes de la session, que le solde dû n'intègre qu'à la clôture.
  const ventesEnCours = compte.data
    ? arrondi(
        compte.data.plafondTiroirEspeces +
          compte.data.plafondTiroirTpe +
          compte.data.plafondCompteEspeces +
          compte.data.plafondCompteTpe -
          compte.data.solde,
      )
    : 0;

  return (
    <div className="mon-compte-ecran">
      <div className={`tresorerie-entete${compte.data?.sessionOuverte ? " en-haut" : ""}`}>
        <div className="tresorerie-solde">
          <span>Solde dû à la caisse principale</span>
          <strong className="tresorerie-solde-montant">
            {compte.data ? decimal(compte.data.solde) : "…"} <span>DH</span>
          </strong>
          {compte.data && <RepartitionDu especes={compte.data.soldeEspeces} tpe={compte.data.soldeTpe} />}
          {compte.data?.sessionOuverte && (
            // Sous le solde, sans s'y ajouter : ces ventes sont encore dans le tiroir.
            <span className="tresorerie-ventes-en-cours">
              <span className="pastille-ouverte" aria-hidden="true" />
              <span>
                <strong>{session?.caisseNom ?? "Caisse"} ouverte</strong> · ventes en cours{" "}
                <strong>{dh(ventesEnCours)}</strong>, ajoutées à la clôture
              </span>
            </span>
          )}
        </div>
        {compte.data?.sessionOuverte && (
          <div className="tiroir-et-compte-bloc">
            <span className="texte-secondaire">
              <strong>{dh(arrondi(compte.data.solde + ventesEnCours))}</strong> = solde dû + ventes en cours
            </span>
            <TiroirEtCompte
              tiroirEspeces={compte.data.plafondTiroirEspeces}
              tiroirTpe={compte.data.plafondTiroirTpe}
              compteEspeces={compte.data.plafondCompteEspeces}
              compteTpe={compte.data.plafondCompteTpe}
            />
          </div>
        )}
        <button
          type="button"
          className="bouton-compact bouton-avec-pastille"
          onClick={() => setPopup("valider")}
        >
          À valider
          {aValider > 0 && (
            <span className="pastille-nombre" aria-label={`${aValider} à valider`}>
              {aValider}
            </span>
          )}
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
