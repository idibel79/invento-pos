import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { decimal, dh, heure, lireMontant } from "../ui/format";
import { IconeCaisse } from "../ui/icones";
import { apercuCloture, arrondi, ecartsParMode, lireComptage } from "./cloture";
import { cles, useCloturerSession, useParametres } from "./requetes";

interface Props {
  session: Schemas["SessionCaisseDto"];
  nombreEnAttente: number;
  onFermer: () => void;
}

const signe = (montant: number) => `${montant > 0 ? "+" : ""}${dh(arrondi(montant))}`;

/**
 * Clôture : le caissier compte le tiroir (espèces et tickets TPE), voit l'écart, puis vide le tiroir en
 * choisissant ce qu'il verse à la caisse principale ; le reste demeure sur son compte. Les ventes en
 * attente sont annulées par le serveur, qui recalcule tous les montants.
 */
export function ClotureDialogue({ session, nombreEnAttente, onFermer }: Props) {
  const queryClient = useQueryClient();
  const cloturer = useCloturerSession();
  const parametres = useParametres();
  const [saisieEspeces, setSaisieEspeces] = useState("");
  const [saisieTpe, setSaisieTpe] = useState("");
  const [saisieVerse, setSaisieVerse] = useState<string | null>(null);
  const tiroir = session.tiroir;
  const attendu = tiroir?.attendu ?? session.fondInitial;
  const soldeAvant = tiroir?.soldeCompte ?? 0;
  const comptage = lireComptage(saisieEspeces, saisieTpe);
  const compte = comptage?.total ?? null;
  // Par défaut, tout le compté est versé à la caisse principale.
  const verse = saisieVerse === null ? compte : lireMontant(saisieVerse);
  const apercu = apercuCloture(attendu, soldeAvant, compte, verse);
  const ecarts = ecartsParMode(
    tiroir?.attenduEspeces ?? session.fondInitial,
    tiroir?.attenduTpe ?? 0,
    comptage,
  );
  const resultat = cloturer.data;

  function terminer() {
    queryClient.setQueryData(cles.sessionCourante, null);
    onFermer();
  }

  if (resultat) {
    const ecart = resultat.ecart ?? 0;
    return (
      <Dialogue titre="Caisse clôturée" ton="succes" onFermer={terminer} largeur={480} classe="cloture">
        <div className="encadre-gris">
          <div className="encadre-ligne">
            <span>Attendu dans le tiroir</span>
            <span>{dh(resultat.tiroir?.attendu ?? 0)}</span>
          </div>
          <div className="encadre-ligne">
            <span>Compté</span>
            <span>{dh(resultat.montantCompte ?? 0)}</span>
          </div>
          <div className="encadre-ligne encadre-ligne-legere">
            <span>dont espèces · écart</span>
            <span>
              {dh(resultat.montantCompteEspeces ?? 0)} · {signe(resultat.ecartEspeces ?? 0)}
            </span>
          </div>
          <div className="encadre-ligne encadre-ligne-legere">
            <span>dont tickets TPE · écart</span>
            <span>
              {dh(resultat.montantCompteTpe ?? 0)} · {signe(resultat.ecartTpe ?? 0)}
            </span>
          </div>
          <div className="encadre-ligne">
            <span>Versé à la caisse principale (en transit)</span>
            <span>{dh(verse ?? 0)}</span>
          </div>
        </div>
        <div className={`rendu${ecart < 0 ? " manque" : ""}`}>
          <span className="libelle">Écart</span>
          <strong>{signe(ecart)}</strong>
        </div>
        <div className="rangee-boutons">
          <button type="button" className="bouton bouton-plein" onClick={terminer} autoFocus>
            Terminer
          </button>
        </div>
      </Dialogue>
    );
  }

  const ecart = apercu.ecart;
  return (
    <Dialogue
      titre="Clôturer la caisse"
      sousTitre={`${session.caisseNom} · ouverte à ${heure(session.ouverture, parametres.data?.fuseauHoraire)}`}
      icone={<IconeCaisse />}
      onFermer={onFermer}
      largeur={860}
      classe="cloture cloture-deux-colonnes"
    >
      <div className="cloture-colonnes">
        <div className="cloture-colonne">
          <h3 className="cloture-titre">Récapitulatif</h3>
          <div className="encadre-gris">
            <Ligne libelle="Fond de caisse" montant={session.fondInitial} />
            <Ligne libelle="Ventes espèces" montant={tiroir?.ventesEspeces ?? 0} plus />
            <Ligne libelle="Ventes carte (tickets TPE)" montant={tiroir?.ventesCarte ?? 0} plus />
            <Ligne libelle="Reçu de la caisse principale" montant={tiroir?.recu ?? 0} plus />
            <Ligne libelle="Versé pendant la session" montant={-(tiroir?.verse ?? 0)} plus />
            <div className="encadre-ligne encadre-total">
              <span>Attendu dans le tiroir</span>
              <span>{dh(attendu)}</span>
            </div>
          </div>
          <h3 className="cloture-titre">Dettes</h3>
          <div className="encadre-gris">
            <div className="encadre-ligne">
              <span>Reste dû</span>
              <span className="cloture-reste-du">{dh(apercu.resteDu)}</span>
            </div>
          </div>
        </div>

        <div className="cloture-colonne">
          <label className="cloture-titre" htmlFor="montant-compte-especes">
            1. Compté dans le tiroir (espèces)
          </label>
          <div className="champ-montant">
            <input
              id="montant-compte-especes"
              inputMode="decimal"
              autoComplete="off"
              placeholder={decimal(0)}
              value={saisieEspeces}
              onChange={(e) => setSaisieEspeces(e.target.value)}
              autoFocus
            />
            <span>DH</span>
          </div>
          <label className="cloture-titre" htmlFor="montant-compte-tpe">
            Compté dans le tiroir (tickets TPE)
          </label>
          <div className="champ-montant">
            <input
              id="montant-compte-tpe"
              inputMode="decimal"
              autoComplete="off"
              placeholder={decimal(0)}
              value={saisieTpe}
              onChange={(e) => setSaisieTpe(e.target.value)}
            />
            <span>DH</span>
          </div>
          <div
            className={`cloture-ecart${ecart === null ? " vide" : ecart < 0 ? " manque" : " excedent"}`}
            role="status"
          >
            <span>Écart</span>
            {ecart !== null && <strong>{signe(ecart)}</strong>}
            {ecart !== null && ecart < 0 && (
              <span className="cloture-ecart-note">à compléter de votre poche</span>
            )}
            {ecart !== null && ecart > 0 && (
              <span className="cloture-ecart-note">ajouté à votre solde dû</span>
            )}
          </div>
          {ecarts && (
            <p className="cloture-ecarts-modes texte-secondaire">
              Espèces <strong>{signe(ecarts.especes)}</strong> · Tickets TPE{" "}
              <strong>{signe(ecarts.tpe)}</strong>
            </p>
          )}

          <label className="cloture-titre" htmlFor="montant-verse">
            2. Montant versé à la caisse principale
          </label>
          <div className="champ-montant">
            <input
              id="montant-verse"
              inputMode="decimal"
              autoComplete="off"
              placeholder={decimal(0)}
              value={saisieVerse ?? (compte === null ? "" : decimal(compte))}
              onChange={(e) => setSaisieVerse(e.target.value)}
            />
            <span>DH</span>
          </div>
          <div className="cloture-raccourcis">
            <button type="button" className="bouton-petit" onClick={() => setSaisieVerse(null)}>
              Tout le compté
            </button>
            <button type="button" className="bouton-petit" onClick={() => setSaisieVerse("0")}>
              Rien
            </button>
          </div>
          {compte !== null && verse !== null && !apercu.verseValide && (
            <p className="erreur-champ">Le montant versé doit être compris entre 0 et le compté.</p>
          )}
          {compte !== null && apercu.verseValide && (
            <p className="texte-secondaire">
              Le tiroir est vidé : {dh(apercu.resteAvecVous)} reste{apercu.resteAvecVous > 1 ? "nt" : ""} avec
              vous, sous votre responsabilité.
            </p>
          )}
        </div>
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
          disabled={compte === null || verse === null || !apercu.verseValide || cloturer.isPending}
          onClick={() =>
            comptage !== null &&
            verse !== null &&
            cloturer.mutate({
              id: session.id,
              montantCompteEspeces: comptage.especes,
              montantCompteTpe: comptage.tpe,
              montantVerse: arrondi(verse),
            })
          }
        >
          Clôturer et verser {dh(verse ?? 0)}
        </button>
      </div>
    </Dialogue>
  );
}

function Ligne({ libelle, montant, plus }: { libelle: string; montant: number; plus?: boolean }) {
  return (
    <div className="encadre-ligne encadre-ligne-legere">
      <span>{libelle}</span>
      <span>{plus ? signe(montant) : dh(montant)}</span>
    </div>
  );
}
