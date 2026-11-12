import type { UseQueryResult } from "@tanstack/react-query";
import { useState } from "react";
import { createPortal } from "react-dom";
import { messageErreur } from "../api/erreurs";
import { useParametres } from "../caisse/requetes";
import { useImpressionFacture } from "../facture/FactureA4";
import { Dialogue } from "../ui/Dialogue";
import { dateHeure, dh } from "../ui/format";
import {
  type Client,
  type MouvementClient,
  libelleSolde,
  texteSolde,
  useAnnulerRemboursement,
  useReleve,
} from "./requetes";

/** Libellé d'une ligne du relevé et son détail (mode, par qui). */
export function libelleMouvement(m: MouvementClient): { libelle: string; detail: string } {
  const par = m.caissePrincipale ? `${m.utilisateur} (caisse principale)` : m.utilisateur;
  switch (m.type) {
    case "VENTE_CREDIT":
      return { libelle: `Vente à crédit ${m.venteNumero ?? ""}`.trim(), detail: par };
    case "REMBOURSEMENT":
      return { libelle: m.annule ? "Remboursement annulé" : "Remboursement", detail: `${modes(m)} · ${par}` };
    case "ANNULATION_REMBOURSEMENT":
      return { libelle: "Annulation du remboursement", detail: `${m.motif ?? ""} · ${par}` };
  }
}

/** Mode d'une ligne : espèces et/ou TPE pour un remboursement, crédit pour une vente. */
function modeLigne(m: MouvementClient): string {
  if (m.type === "VENTE_CREDIT") return "Crédit";
  const parts = [];
  if (m.especes) parts.push("Espèces");
  if (m.tpe) parts.push("TPE");
  return parts.join(" + ");
}

function modes(m: MouvementClient): string {
  const parts = [];
  if (m.especes) parts.push(`espèces ${dh(m.especes)}`);
  if (m.tpe) parts.push(`TPE ${dh(m.tpe)}`);
  return parts.join(" + ");
}

/**
 * Relevé d'un client, du plus récent au plus ancien ; `annulable` (admin) propose d'annuler un
 * remboursement saisi par erreur.
 */
export function Releve({
  requete,
  annulable = false,
  limite,
}: {
  requete: UseQueryResult<MouvementClient[]>;
  annulable?: boolean;
  limite?: number;
}) {
  const fuseau = useParametres().data?.fuseauHoraire;
  const [aAnnuler, setAAnnuler] = useState<MouvementClient | null>(null);

  if (requete.isPending) return <p className="vide">Chargement…</p>;
  if (requete.isError)
    return (
      <p className="alerte alerte-danger" role="alert">
        {messageErreur(requete.error)}
      </p>
    );
  const lignes = limite ? requete.data.slice(0, limite) : requete.data;
  if (lignes.length === 0) return <p className="vide">Aucun mouvement.</p>;

  return (
    <>
      <ul className="releve">
        {lignes.map((m) => {
          const { libelle, detail } = libelleMouvement(m);
          return (
            <li key={m.id} className={`releve-ligne${m.annule ? " annule" : ""}`}>
              <span className="releve-libelle">{libelle}</span>
              <strong className={`releve-montant ${m.montant > 0 ? "texte-dette" : "texte-succes"}`}>
                {m.montant > 0 ? "+ " : "− "}
                {dh(Math.abs(m.montant))}
              </strong>
              <span className="texte-secondaire">
                {dateHeure(m.le, fuseau)} · {detail}
              </span>
              <span className="texte-secondaire a-droite">dette {texteSolde(m.detteApres)}</span>
              {annulable && m.type === "REMBOURSEMENT" && !m.annule && (
                <button type="button" className="lien-action" onClick={() => setAAnnuler(m)}>
                  Annuler ce remboursement…
                </button>
              )}
            </li>
          );
        })}
      </ul>
      {aAnnuler && <AnnulationDialogue remboursement={aAnnuler} onFermer={() => setAAnnuler(null)} />}
    </>
  );
}

/** Annulation par l'admin : motif obligatoire, la dette remonte et le compte de l'encaisseur est débité. */
function AnnulationDialogue({
  remboursement,
  onFermer,
}: {
  remboursement: MouvementClient;
  onFermer: () => void;
}) {
  const annuler = useAnnulerRemboursement();
  const [motif, setMotif] = useState("");
  const montant = Math.abs(remboursement.montant);

  return (
    <Dialogue titre="Annuler ce remboursement ?" sousTitre={remboursement.client} onFermer={onFermer}>
      <form
        className="formulaire"
        onSubmit={(e) => {
          e.preventDefault();
          if (motif.trim())
            annuler.mutate({ id: remboursement.id, motif: motif.trim() }, { onSuccess: onFermer });
        }}
      >
        <p>
          La dette de {remboursement.client} remontera de <strong>{dh(montant)}</strong>, et{" "}
          {remboursement.caissePrincipale
            ? "la caisse principale"
            : `le compte de ${remboursement.utilisateur}`}{" "}
          baissera du même montant. Rien n'est effacé : une ligne d'annulation est ajoutée au relevé.
        </p>
        <label className="champ">
          <span className="libelle">Motif (obligatoire)</span>
          <input
            autoFocus
            maxLength={300}
            placeholder="Ex. mauvais client, mauvais montant"
            value={motif}
            onChange={(e) => setMotif(e.target.value)}
          />
        </label>
        {annuler.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(annuler.error)}
          </p>
        )}
        <div className="rangee-boutons">
          <button type="button" className="bouton bouton-contour" onClick={onFermer}>
            Retour
          </button>
          <button
            type="submit"
            className="bouton bouton-danger"
            disabled={!motif.trim() || annuler.isPending}
          >
            Annuler le remboursement
          </button>
        </div>
      </form>
    </Dialogue>
  );
}

/**
 * Relevé d'un client en popup (design B) : une ligne par opération (date, opération, mode, par qui,
 * montant, solde), annulation d'un remboursement pour l'admin, impression et fermeture.
 */
export function ReleveDialogue({
  client,
  onFermer,
  annulable = true,
}: {
  client: Client;
  onFermer: () => void;
  /** Annulation d'un remboursement proposée (admin seul ; le serveur vérifie). */
  annulable?: boolean;
}) {
  const releve = useReleve(client.id);
  const fuseau = useParametres().data?.fuseauHoraire;
  const [aAnnuler, setAAnnuler] = useState<MouvementClient | null>(null);
  const facture = useImpressionFacture();
  const lignes = releve.data ?? [];
  // Le relevé est relu après une annulation : sa première ligne porte la dette à jour.
  const dette = lignes[0]?.detteApres ?? client.dette;

  const tableau = (impression: boolean) => (
    <table className="releve-tableau">
      <thead>
        <tr>
          <th scope="col">Date</th>
          <th scope="col">Opération</th>
          <th scope="col">Mode</th>
          <th scope="col">Par</th>
          <th scope="col" className="a-droite">
            Montant
          </th>
          <th scope="col" className="a-droite">
            Solde
          </th>
          {!impression && (
            <th scope="col">
              <span className="visuellement-cache">Actions</span>
            </th>
          )}
        </tr>
      </thead>
      <tbody>
        {lignes.map((m) => {
          const { libelle } = libelleMouvement(m);
          return (
            <tr key={m.id} className={m.annule ? "annule" : undefined}>
              <td>{dateHeure(m.le, fuseau)}</td>
              <td>
                {libelle}
                {m.type === "ANNULATION_REMBOURSEMENT" && m.motif && (
                  <span className="texte-secondaire"> · {m.motif}</span>
                )}
              </td>
              <td>{modeLigne(m)}</td>
              <td>{m.caissePrincipale ? `${m.utilisateur} (caisse principale)` : m.utilisateur}</td>
              <td className={`a-droite ${m.montant > 0 ? "texte-dette" : "texte-succes"}`}>
                {m.montant > 0 ? "+ " : "− "}
                {dh(Math.abs(m.montant))}
              </td>
              <td className="a-droite">{texteSolde(m.detteApres)}</td>
              {!impression && (
                <td>
                  {annulable && m.type === "REMBOURSEMENT" && !m.annule && (
                    <button type="button" className="lien-action" onClick={() => setAAnnuler(m)}>
                      Annuler…
                    </button>
                  )}
                  {client.type === "ENTREPRISE" && m.type === "VENTE_CREDIT" && m.venteId != null && (
                    <button
                      type="button"
                      className="lien-action"
                      disabled={facture.enCours}
                      onClick={() => m.venteId != null && facture.imprimer(m.venteId)}
                    >
                      Facture A4
                    </button>
                  )}
                </td>
              )}
            </tr>
          );
        })}
      </tbody>
    </table>
  );

  const entete = (
    <div className="releve-entete">
      <div>
        <strong className="releve-client">{client.nom}</strong>
        {client.telephone && <span className="texte-secondaire">{client.telephone}</span>}
      </div>
      <div className="a-droite">
        <span className="texte-secondaire">{libelleSolde(dette)} actuelle</span>
        <strong className={dette > 0 ? "texte-dette releve-dette" : "releve-dette"}>
          {dh(Math.abs(dette))}
        </strong>
      </div>
    </div>
  );

  return (
    <Dialogue titre={`Relevé · ${client.nom}`} onFermer={onFermer} largeur={880}>
      <div className="formulaire releve-popup">
        {entete}
        {releve.isPending && <p className="vide">Chargement…</p>}
        {releve.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(releve.error)}
          </p>
        )}
        {releve.isSuccess && lignes.length === 0 && <p className="vide">Aucun mouvement.</p>}
        {lignes.length > 0 && <div className="releve-defilement">{tableau(false)}</div>}
        {facture.erreur && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(facture.erreur)}
          </p>
        )}
        <div className="rangee-boutons">
          <span className="texte-secondaire releve-aide">
            {annulable &&
              "Annuler un remboursement saisi par erreur : « Annuler… » sur sa ligne (motif obligatoire)."}
          </span>
          <button
            type="button"
            className="bouton bouton-contour"
            disabled={lignes.length === 0}
            onClick={() => window.print()}
          >
            Imprimer
          </button>
          <button type="button" className="bouton bouton-plein" onClick={onFermer}>
            Fermer
          </button>
        </div>
      </div>
      {aAnnuler && <AnnulationDialogue remboursement={aAnnuler} onFermer={() => setAAnnuler(null)} />}
      {facture.rendu}
      {createPortal(
        <div className="zone-impression">
          <div className="releve-impression">
            <h1>Relevé client</h1>
            {entete}
            {tableau(true)}
          </div>
        </div>,
        document.body,
      )}
    </Dialogue>
  );
}
