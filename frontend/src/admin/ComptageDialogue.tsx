import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { apercuCloture, arrondi } from "../caisse/cloture";
import { useTerminerComptage } from "../tresorerie/requetes";
import { Dialogue } from "../ui/Dialogue";
import { dateHeure, decimal, dh, lireMontant } from "../ui/format";

interface Props {
  session: Schemas["SessionCaisseDto"];
  caissier: string;
  fuseau: string | undefined;
  onFermer: () => void;
}

/**
 * Comptage par l'admin du tiroir d'une session clôturée d'office : écart calculé comme à une clôture,
 * puis ce qui est versé entre directement dans la caisse principale (l'admin a le tiroir en main).
 */
export function ComptageDialogue({ session, caissier, fuseau, onFermer }: Props) {
  const terminer = useTerminerComptage();
  const [saisieCompte, setSaisieCompte] = useState("");
  const [saisieVerse, setSaisieVerse] = useState<string | null>(null);
  const attendu = session.tiroir?.attendu ?? session.fondInitial;
  const compte = lireMontant(saisieCompte);
  const verse = saisieVerse === null ? compte : lireMontant(saisieVerse);
  const apercu = apercuCloture(attendu, 0, compte, verse);
  const ecart = apercu.ecart;

  return (
    <Dialogue
      titre="Terminer le comptage"
      sousTitre={`${session.caisseNom} · ${caissier} · clôturée d'office le ${dateHeure(session.fermeture ?? session.ouverture, fuseau)}`}
      onFermer={onFermer}
      largeur={480}
      classe="cloture"
    >
      <div className="encadre-gris">
        <div className="encadre-ligne encadre-ligne-legere">
          <span>Fond de caisse</span>
          <span>{dh(session.fondInitial)}</span>
        </div>
        <div className="encadre-ligne encadre-ligne-legere">
          <span>Ventes (espèces + carte)</span>
          <span>{dh((session.tiroir?.ventesEspeces ?? 0) + (session.tiroir?.ventesCarte ?? 0))}</span>
        </div>
        <div className="encadre-ligne encadre-total">
          <span>Attendu dans le tiroir</span>
          <span>{dh(attendu)}</span>
        </div>
      </div>
      <label className="cloture-titre" htmlFor="comptage-compte">
        Compté dans le tiroir (espèces + tickets TPE)
      </label>
      <div className="champ-montant">
        <input
          id="comptage-compte"
          inputMode="decimal"
          autoComplete="off"
          placeholder={decimal(0)}
          value={saisieCompte}
          onChange={(e) => setSaisieCompte(e.target.value)}
          autoFocus
        />
        <span>DH</span>
      </div>
      <div
        className={`cloture-ecart${ecart === null ? " vide" : ecart < 0 ? " manque" : ecart > 0 ? " excedent" : ""}`}
      >
        <span>Écart</span>
        <strong>{ecart === null ? "—" : `${ecart > 0 ? "+" : ""}${dh(ecart)}`}</strong>
      </div>
      <label className="cloture-titre" htmlFor="comptage-verse">
        Versé à la caisse principale
      </label>
      <div className="champ-montant">
        <input
          id="comptage-verse"
          inputMode="decimal"
          autoComplete="off"
          placeholder={decimal(0)}
          value={saisieVerse ?? saisieCompte}
          onChange={(e) => setSaisieVerse(e.target.value)}
        />
        <span>DH</span>
      </div>
      <p className="texte-secondaire">
        Le reste ({dh(apercu.resteAvecVous)}) demeure sur le compte de {caissier}.
      </p>
      {terminer.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(terminer.error)}
        </p>
      )}
      <div className="rangee-boutons">
        <button type="button" className="bouton bouton-contour" onClick={onFermer}>
          Annuler
        </button>
        <button
          type="button"
          className="bouton bouton-plein"
          disabled={compte === null || verse === null || !apercu.verseValide || terminer.isPending}
          onClick={() =>
            compte !== null &&
            verse !== null &&
            terminer.mutate(
              { id: session.id, montantCompte: compte, montantVerse: arrondi(verse) },
              { onSuccess: onFermer },
            )
          }
        >
          Terminer le comptage
        </button>
      </div>
    </Dialogue>
  );
}
