import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useCaisses } from "../caisse/requetes";
import { FiltreJour } from "../ui/ChoixJour";
import { decimal, heure } from "../ui/format";
import { type FiltreJournal, type StatutJournal, type TypeJournal, useJournalTresorerie } from "./requetes";
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

/** Mêmes colonnes pour tous les comptes ; la dernière est le solde du compte consulté après l'opération. */
function colonnes(monCompte: boolean): [string, boolean][] {
  return [
    ["Date", false],
    ["Type", false],
    ["Opération", false],
    ["Caisse", false],
    ["Utilisateur", false],
    ["Montant", true],
    ["Espèces", true],
    ["TPE", true],
    ["Statut", false],
    [monCompte ? "Mon solde avant" : "Solde avant", true],
  ];
}

/**
 * Type de la ligne : Ventes pour une clôture de session (ventes + excédent), sinon Crédit quand le compte reçoit
 * de l'argent et Versement quand il en verse ({@code montant} est signé du point de vue du journal affiché).
 */
function typeLigne(o: Operation, montant: number): string {
  if (o.type === "CLOTURE_SESSION") return "Ventes";
  if (estDette(o)) return "Dette";
  return montant < 0 ? "Versement" : "Crédit";
}

/** Remboursement de dette d'un client, ou son annulation. */
function estDette(o: Operation): boolean {
  return o.type === "REMBOURSEMENT_DETTE" || o.type === "ANNULATION_DETTE";
}

/**
 * Colonne Utilisateur : l'autre partie de l'opération. Dans le journal de la caisse principale, le caissier ou le
 * responsable concerné ; dans celui d'un caissier, côté caisse principale, l'admin qui lui a versé ou qui a traité
 * son versement (personne tant qu'il est en transit). Sinon, qui a fait l'opération.
 */
function utilisateur(o: Operation, vueCaissier: boolean): string | null {
  if (estDette(o)) return o.client ?? o.auteur;
  if (!vueCaissier) return o.caissier ?? o.auteur;
  if (o.versementId == null) return o.auteur;
  return o.versLaPrincipale ? o.traitePar : o.auteur;
}

/**
 * Colonne Caisse : celle de la session, sinon « Caisse principale » pour un versement hors session (alimentation
 * par la caisse principale, versement à la caisse principale).
 */
function caisseLigne(o: Operation): string | null {
  // Dette : l'argent vient d'un client.
  if (estDette(o)) return "Client";
  if (o.caisse) return o.caisse;
  return o.versementId != null ? "Caisse principale" : null;
}

/**
 * Colonnes Espèces et TPE : la répartition saisie ; un versement de la caisse principale à un caissier (alimentation)
 * est toujours en espèces.
 */
function repartition(o: Operation): [number | null, number | null] {
  if (o.especes == null && o.tpe == null && o.versementId != null && o.versLaPrincipale === false) {
    return [Math.abs(o.montant), 0];
  }
  return [o.especes, o.tpe];
}

/** Libellé court de l'opération, et son complément en gris (motif ; la caisse a sa colonne). */
function libelle(o: Operation, monCompte: boolean): [string, string] {
  const complement = o.motif ?? "";
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
      return ["Clôture", complement];
    case "REMBOURSEMENT_DETTE":
      // Le client est dans la colonne Utilisateur : pas de complément.
      return ["Remboursement de dette", ""];
    case "ANNULATION_DETTE":
      return ["Remboursement de dette annulé", complement];
  }
}

interface Props {
  fuseau: string | undefined;
  /**
   * Journal de l'utilisateur connecté (rubrique Mon compte) : ses versements, alimentations et ventes, montants vus
   * de son compte, sans filtre d'utilisateur ni solde de la caisse principale.
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
  const [type, setType] = useState<TypeJournal>();
  const [croissant, setCroissant] = useState(false);
  const jourCourant = aujourdhui(fuseau);
  // undefined : aujourd'hui par défaut ; null : tous les jours.
  const jour = jourChoisi === undefined ? jourCourant : (jourChoisi ?? undefined);
  const filtre: FiltreJournal = { jour, caisseId, utilisateurId, statut, type, croissant };
  const journal = useJournalTresorerie(filtre, monCompte);
  const lignes = journal.data?.pages.flatMap((p) => p.contenu) ?? [];
  const titre = monCompte ? "Journal de mon compte" : "Journal de la caisse principale";
  // Journal d'un caissier ou d'un responsable (choisi ou filtré) : vu de son compte, comme Mon compte.
  const vueCaissier = monCompte || utilisateurId != null;

  const date = (iso: string) => {
    const j = new Intl.DateTimeFormat("en-CA", { timeZone: fuseau }).format(new Date(iso));
    const prefixe = j === jourCourant ? "Auj." : `${j.slice(8, 10)}/${j.slice(5, 7)}`;
    return `${prefixe} ${heure(iso, fuseau)}`;
  };

  return (
    <section className="carte-journal" aria-label={titre}>
      <div className="carte-journal-tete">
        <h2>{titre}</h2>
        <FiltreJour jour={jour ?? null} aujourdhui={jourCourant} onChange={setJour} compact />
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
        {/* Filtre Utilisateur : journal de la caisse principale seulement, pas sur le compte d'une personne. */}
        {!monCompte && utilisateurInitial == null && (
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
        )}
        <label className="filtre-compact">
          <span className="visuellement-cache">Type</span>
          <select
            value={type ?? ""}
            onChange={(e) => setType((e.target.value || undefined) as TypeJournal | undefined)}
          >
            <option value="">Type ▾</option>
            {/* Les ventes (clôtures) ne figurent que sur le compte d'un caissier ou d'un responsable. */}
            {vueCaissier && <option value="VENTES">Ventes</option>}
            <option value="DETTE">Dette</option>
            <option value="CREDIT">Crédit</option>
            <option value="VERSEMENT">Versement</option>
          </select>
        </label>
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
      </div>
      {journal.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(journal.error)}
        </p>
      )}
      <div className="journal-tresorerie" role="table" aria-label="Opérations">
        <div className="journal-ligne journal-entete" role="row">
          {colonnes(monCompte).map(([titreColonne, droite]) =>
            titreColonne === "Date" ? (
              // Tri par date : un clic inverse l'ordre, la flèche montre le sens.
              <span key={titreColonne} role="columnheader" aria-sort={croissant ? "ascending" : "descending"}>
                <button
                  type="button"
                  className="entete-tri actif"
                  title={croissant ? "Anciennes d'abord" : "Récentes d'abord"}
                  onClick={() => setCroissant((c) => !c)}
                >
                  Date
                  {/* Mêmes flèches que le tri du catalogue (écran de vente). */}
                  <svg className="icone-tri" width="10" height="14" viewBox="0 0 10 14" aria-hidden="true">
                    <path d="M5 0L10 5.5H0Z" className={croissant ? "tri-actif" : undefined} />
                    <path d="M5 14L0 8.5H10Z" className={croissant ? undefined : "tri-actif"} />
                  </svg>
                </button>
              </span>
            ) : (
              <span key={titreColonne} role="columnheader" className={droite ? "a-droite" : undefined}>
                {titreColonne}
              </span>
            ),
          )}
        </div>
        {journal.isSuccess && lignes.length === 0 && <p className="vide">Aucune opération.</p>}
        {lignes.map((o) => {
          const [texte, complement] = libelle(o, vueCaissier);
          // Le serveur signe un versement du point de vue de la caisse principale ; un caissier le voit de l'autre côté.
          const montant = vueCaissier && o.versementId != null ? -o.montant : o.montant;
          // Solde du compte consulté juste avant l'opération.
          const soldeAvant = vueCaissier ? o.soldeCaissierAvant : o.soldePrincipaleAvant;
          const cloture = o.type === "CLOTURE_SESSION";
          return (
            <div key={o.id} className="journal-ligne" role="row">
              <span role="cell" className="texte-gris">
                {date(o.le)}
              </span>
              <span role="cell">{typeLigne(o, montant)}</span>
              <span role="cell">
                <span className="journal-operation">{texte}</span>
                {complement && <span className="texte-gris"> · {complement}</span>}
              </span>
              <span role="cell" className={caisseLigne(o) ? undefined : "texte-gris"}>
                {caisseLigne(o) ?? "—"}
              </span>
              <span role="cell">{utilisateur(o, vueCaissier) ?? "—"}</span>
              <span role="cell" className={`a-droite montant-signe ${montant < 0 ? "negatif" : "positif"}`}>
                {signe(montant)}
              </span>
              {/* Répartition du versement (ou du tiroir compté à la clôture) entre espèces et tickets TPE. */}
              {repartition(o).map((part, i) => (
                <span key={i} role="cell" className="a-droite texte-gris">
                  {part == null ? "—" : decimal(part)}
                </span>
              ))}
              <span role="cell">
                {/* Une clôture est validée d'office : affichée comme auto-validée. */}
                <StatutVersement
                  statut={o.statut}
                  autoValide={cloture || o.autoValide}
                  versLaPrincipale={o.versLaPrincipale}
                />
              </span>
              <span role="cell" className="a-droite">
                {soldeAvant == null ? "—" : decimal(soldeAvant)}
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
