import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { decimal, dh, heure } from "../ui/format";
import { IconeCaisse } from "../ui/icones";
import { arrondi, ecartsParMode, lireComptage } from "./cloture";
import { cles, useCloturerSession, useParametres } from "./requetes";

interface Props {
  session: Schemas["SessionCaisseDto"];
  nombreEnAttente: number;
  onFermer: () => void;
}

const signe = (montant: number) => `${montant > 0 ? "+" : ""}${dh(arrondi(montant))}`;

/**
 * Clôture : le caissier compte le tiroir (espèces et tickets TPE) et voit l'écart. Rien n'est versé à la
 * caisse principale : le compté reste sur son compte, il le verse ensuite depuis « Mon compte ». Les
 * ventes en attente sont annulées par le serveur, qui recalcule tous les montants.
 */
export function ClotureDialogue({ session, nombreEnAttente, onFermer }: Props) {
  const queryClient = useQueryClient();
  const cloturer = useCloturerSession();
  const parametres = useParametres();
  const [saisieEspeces, setSaisieEspeces] = useState("");
  const [saisieTpe, setSaisieTpe] = useState("");
  const tiroir = session.tiroir;
  const attendu = tiroir?.attendu ?? session.fondInitial;
  const comptage = lireComptage(saisieEspeces, saisieTpe);
  const ecart = comptage === null ? null : arrondi(comptage.total - attendu);
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
    return (
      <Dialogue titre="Caisse clôturée" ton="succes" onFermer={terminer} largeur={480} classe="cloture">
        <div className="encadre-gris">
          <div className="encadre-ligne">
            <span>Attendu dans le tiroir (espèces + TPE)</span>
            <span>{dh(resultat.tiroir?.attendu ?? 0)}</span>
          </div>
          <Ligne libelle="Compté espèces" montant={resultat.montantCompteEspeces ?? 0} />
          <Ligne libelle="Compté TPE" montant={resultat.montantCompteTpe ?? 0} />
        </div>
        <Ecarts
          especes={resultat.ecartEspeces ?? 0}
          tpe={resultat.ecartTpe ?? 0}
          total={resultat.ecart ?? 0}
        />
        <div className="rangee-boutons">
          <button type="button" className="bouton bouton-plein" onClick={terminer} autoFocus>
            Terminer
          </button>
        </div>
      </Dialogue>
    );
  }

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
          <div className="encadre-gris cloture-recap">
            <Ligne libelle="Fond de caisse espèces" montant={session.fondInitial} />
            <Ligne libelle="Ventes espèces" montant={tiroir?.ventesEspeces ?? 0} plus />
            <Ligne libelle="Ventes carte (tickets TPE)" montant={tiroir?.ventesCarte ?? 0} plus />
            <div className="encadre-ligne encadre-total">
              <span>Attendu dans le tiroir (espèces + TPE)</span>
              <span>{dh(attendu)}</span>
            </div>
          </div>
        </div>

        <div className="cloture-colonne">
          <label className="cloture-titre" htmlFor="montant-compte-especes">
            Compté dans le tiroir (espèces)
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
          <Ecarts especes={ecarts?.especes ?? null} tpe={ecarts?.tpe ?? null} total={ecart} />
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
          disabled={comptage === null || cloturer.isPending}
          onClick={() =>
            comptage !== null &&
            cloturer.mutate({
              id: session.id,
              montantCompteEspeces: comptage.especes,
              montantCompteTpe: comptage.tpe,
            })
          }
        >
          Clôturer la caisse
        </button>
      </div>
    </Dialogue>
  );
}

/** Écart espèces, écart TPE et écart total sur trois lignes fixes ; « — » tant que rien n'est compté. */
function Ecarts({
  especes,
  tpe,
  total,
}: {
  especes: number | null;
  tpe: number | null;
  total: number | null;
}) {
  const ton = total === null ? " vide" : total < 0 ? " manque" : " excedent";
  const montant = (valeur: number | null) => (valeur === null ? "—" : signe(valeur));
  return (
    <div className={`cloture-ecart cloture-ecart-lignes${ton}`} role="status">
      <div className="cloture-ecart-ligne">
        <span>Écart espèces</span>
        <span>{montant(especes)}</span>
      </div>
      <div className="cloture-ecart-ligne">
        <span>Écart TPE</span>
        <span>{montant(tpe)}</span>
      </div>
      <div className="cloture-ecart-ligne cloture-ecart-total">
        <span>Écart total</span>
        <strong>{montant(total)}</strong>
      </div>
    </div>
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
