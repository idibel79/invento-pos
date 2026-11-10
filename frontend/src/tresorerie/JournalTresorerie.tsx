import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useCaisses } from "../caisse/requetes";
import { decimal, heure } from "../ui/format";
import { type FiltreJournal, type StatutJournal, useJournalTresorerie } from "./requetes";
import { StatutVersement } from "./StatutVersement";

type Compte = Schemas["CompteCaissierDto"];
type Operation = Schemas["Operation"];

/** Jour courant (AAAA-MM-JJ) dans le fuseau de la boutique. */
function aujourdhui(fuseau: string | undefined): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: fuseau }).format(new Date());
}

/** Montant signé à la française : « + 1 500,00 », « − 200,00 ». */
function signe(montant: number): string {
  if (montant === 0) return decimal(0);
  return `${montant > 0 ? "+" : "−"} ${decimal(Math.abs(montant))}`;
}

const STATUTS: Record<StatutJournal, string> = {
  VALIDE: "Validé",
  AUTO_VALIDE: "Auto-validé",
  EN_TRANSIT: "En transit / à valider",
  REFUSE: "Refusé",
  ANNULE: "Annulé",
};

const COLONNES_PRINCIPALE: [string, boolean][] = [
  ["Date", false],
  ["Opération", false],
  ["Utilisateur", false],
  ["Montant", true],
  ["Écart", true],
  ["Statut", false],
  ["Solde CP après", true],
  ["Solde caissier après", true],
];

const COLONNES_MON_COMPTE: [string, boolean][] = [
  ["Date", false],
  ["Opération", false],
  ["Par", false],
  ["Montant", true],
  ["Ventes", true],
  ["Écart", true],
  ["Statut", false],
  ["Mon solde après", true],
];

/** Libellé court de l'opération, et son complément en gris (caisse, motif). */
function libelle(o: Operation, monCompte: boolean): [string, string] {
  const complement = [o.caisse, o.motif].filter(Boolean).join(" · ");
  switch (o.type) {
    case "ALIMENTATION":
      return [monCompte ? "Alimentation de la caisse principale" : "Alimentation", complement];
    case "RETRAIT":
      return ["Retrait", complement];
    case "VERSEMENT_ENVOYE":
      if (!o.versLaPrincipale) {
        return [monCompte ? "Alimentation par la caisse principale" : "Versement à un caissier", complement];
      }
      return [
        o.cloture
          ? "Versement de clôture"
          : o.caisse
            ? "Versement en cours de session"
            : "Versement à la caisse principale",
        complement,
      ];
    case "VERSEMENT_RECU":
      return [
        o.versLaPrincipale ? "Réception par la caisse principale" : "Réception par le caissier",
        complement,
      ];
    case "VERSEMENT_REFUSE":
      return ["Versement refusé", complement];
    case "VERSEMENT_ANNULE":
      return ["Versement annulé", complement];
    case "AJUSTEMENT":
      return ["Ajustement (perte)", complement];
    case "CLOTURE_SESSION":
      return ["Clôture : ventes + excédent", complement];
  }
}

interface Props {
  fuseau: string | undefined;
  /**
   * Journal de l'utilisateur connecté (rubrique Mon compte) : ses versements, alimentations et ventes, montants vus
   * de son compte, sans filtre de caisse ni d'utilisateur ni solde de la caisse principale.
   */
  monCompte?: boolean;
  /** Caissiers proposés au filtre Utilisateur (journal de la caisse principale). */
  caissiers?: Compte[];
  utilisateurInitial?: number;
}

/** Journal des opérations de trésorerie : filtres compacts, paginé. */
export function JournalTresorerie({ fuseau, monCompte = false, caissiers = [], utilisateurInitial }: Props) {
  const caisses = useCaisses().data ?? [];
  const [jourChoisi, setJour] = useState<string | null>();
  const [caisseId, setCaisseId] = useState<number>();
  const [utilisateurId, setUtilisateurId] = useState<number | undefined>(utilisateurInitial);
  const [statut, setStatut] = useState<StatutJournal>();
  const [croissant, setCroissant] = useState(false);
  const jourCourant = aujourdhui(fuseau);
  // undefined : aujourd'hui par défaut ; null : tous les jours.
  const jour = jourChoisi === undefined ? jourCourant : (jourChoisi ?? undefined);
  const filtre: FiltreJournal = { jour, caisseId, utilisateurId, statut, croissant };
  const journal = useJournalTresorerie(filtre, monCompte);
  const lignes = journal.data?.pages.flatMap((p) => p.contenu) ?? [];
  const titre = monCompte ? "Journal de mon compte" : "Journal de la caisse principale";
  const colonnes = monCompte ? COLONNES_MON_COMPTE : COLONNES_PRINCIPALE;

  const date = (iso: string) => {
    const j = new Intl.DateTimeFormat("en-CA", { timeZone: fuseau }).format(new Date(iso));
    const prefixe = j === jourCourant ? "Auj." : `${j.slice(8, 10)}/${j.slice(5, 7)}`;
    return `${prefixe} ${heure(iso, fuseau)}`;
  };

  return (
    <section className={`carte-journal${monCompte ? " journal-mon-compte" : ""}`} aria-label={titre}>
      <div className="carte-journal-tete">
        <h2>{titre}</h2>
        <label className="filtre-compact">
          <span className="visuellement-cache">Jour</span>
          <input
            type="date"
            value={jour ?? ""}
            max={jourCourant}
            onChange={(e) => setJour(e.target.value || null)}
          />
        </label>
        {jour !== undefined && (
          <button type="button" className="filtre-compact" onClick={() => setJour(null)}>
            Tous les jours
          </button>
        )}
        {!monCompte && (
          <>
            <label className="filtre-compact">
              <span className="visuellement-cache">Caisse</span>
              <select
                value={caisseId ?? ""}
                onChange={(e) => setCaisseId(e.target.value ? Number(e.target.value) : undefined)}
              >
                <option value="">Caisse ▾</option>
                {caisses.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.nom}
                  </option>
                ))}
              </select>
            </label>
            <label className="filtre-compact">
              <span className="visuellement-cache">Utilisateur</span>
              <select
                value={utilisateurId ?? ""}
                onChange={(e) => setUtilisateurId(e.target.value ? Number(e.target.value) : undefined)}
              >
                <option value="">Utilisateur ▾</option>
                {caissiers.map((c) => (
                  <option key={c.utilisateurId} value={c.utilisateurId}>
                    {c.nom}
                  </option>
                ))}
              </select>
            </label>
          </>
        )}
        <label className="filtre-compact">
          <span className="visuellement-cache">Statut</span>
          <select
            value={statut ?? ""}
            onChange={(e) => setStatut((e.target.value || undefined) as StatutJournal | undefined)}
          >
            <option value="">Statut ▾</option>
            {(Object.keys(STATUTS) as StatutJournal[]).map((s) => (
              <option key={s} value={s}>
                {STATUTS[s]}
              </option>
            ))}
          </select>
        </label>
        <label className="filtre-compact">
          <span className="visuellement-cache">Tri</span>
          <select value={croissant ? "asc" : "desc"} onChange={(e) => setCroissant(e.target.value === "asc")}>
            <option value="desc">Récentes d'abord</option>
            <option value="asc">Anciennes d'abord</option>
          </select>
        </label>
      </div>
      {journal.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(journal.error)}
        </p>
      )}
      <div className="journal-tresorerie" role="table" aria-label="Opérations">
        <div className="journal-ligne journal-entete" role="row">
          {colonnes.map(([titreColonne, droite]) => (
            <span key={titreColonne} role="columnheader" className={droite ? "a-droite" : undefined}>
              {titreColonne}
            </span>
          ))}
        </div>
        {journal.isSuccess && lignes.length === 0 && <p className="vide">Aucune opération.</p>}
        {lignes.map((o) => {
          const [texte, complement] = libelle(o, monCompte);
          // Le serveur signe un versement du point de vue de la caisse principale ; Mon compte le voit de l'autre côté.
          const montant = monCompte && o.versementId != null ? -o.montant : o.montant;
          return (
            <div key={o.id} className="journal-ligne" role="row">
              <span role="cell" className="texte-gris">
                {date(o.le)}
              </span>
              <span role="cell">
                <span className="journal-operation">{texte}</span>
                {complement && <span className="texte-gris"> · {complement}</span>}
              </span>
              <span role="cell">{monCompte ? o.auteur : (o.caissier ?? o.auteur)}</span>
              <span role="cell" className={`a-droite montant-signe ${montant < 0 ? "negatif" : "positif"}`}>
                {signe(montant)}
              </span>
              {monCompte && (
                <span role="cell" className={`a-droite${o.ventesSession == null ? " texte-gris" : ""}`}>
                  {o.ventesSession == null ? "—" : decimal(o.ventesSession)}
                </span>
              )}
              <span
                role="cell"
                className={`a-droite${o.ecartSession ? ` montant-signe ${o.ecartSession < 0 ? "negatif" : "positif"}` : " texte-gris"}`}
              >
                {o.ecartSession == null ? "—" : signe(o.ecartSession)}
              </span>
              <span role="cell">
                <StatutVersement
                  statut={o.statut}
                  autoValide={o.autoValide}
                  versLaPrincipale={o.versLaPrincipale}
                />
              </span>
              {!monCompte && (
                <span role="cell" className="a-droite">
                  {decimal(o.soldePrincipaleApres)}
                </span>
              )}
              <span role="cell" className="a-droite">
                {o.soldeCaissierApres == null ? "—" : decimal(o.soldeCaissierApres)}
              </span>
            </div>
          );
        })}
        {journal.hasNextPage && (
          <button
            type="button"
            className="tableau-suite"
            onClick={() => void journal.fetchNextPage()}
            disabled={journal.isFetchingNextPage}
          >
            Afficher plus d'opérations
          </button>
        )}
      </div>
    </section>
  );
}
