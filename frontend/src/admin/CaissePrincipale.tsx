import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useCaisses, useParametres } from "../caisse/requetes";
import {
  type FiltreJournal,
  type StatutJournal,
  useAValiderPrincipale,
  useComptagesEnAttente,
  useComptes,
  useJournalTresorerie,
} from "../tresorerie/requetes";
import { StatutVersement } from "../tresorerie/StatutVersement";
import { VersementAValider } from "../tresorerie/VersementAValider";
import { decimal, heure } from "../ui/format";
import { ComptageDialogue } from "./ComptageDialogue";
import { OperationTresorerieDialogue, type TypeOperation } from "./OperationTresorerieDialogue";

type Selection = "principale" | number;
type Compte = Schemas["CompteCaissierDto"];
type Session = Schemas["SessionCaisseDto"];

/** Jour courant (AAAA-MM-JJ) dans le fuseau de la boutique. */
function aujourdhui(fuseau: string | undefined): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: fuseau }).format(new Date());
}

/** Montant signé à la française : « + 1 500,00 », « − 200,00 ». */
function signe(montant: number): string {
  if (montant === 0) return decimal(0);
  return `${montant > 0 ? "+" : "−"} ${decimal(Math.abs(montant))}`;
}

function SoldeDh({ montant }: { montant: number }) {
  return (
    <strong className="tresorerie-solde-montant">
      {decimal(montant)} <span>DH</span>
    </strong>
  );
}

/**
 * Caisse principale (admin), maquette B : comptes dans une colonne à gauche (caisse principale puis
 * chaque caissier), à droite le compte choisi (solde et opérations), les versements à valider et le
 * journal de toutes les opérations.
 */
export function CaissePrincipale() {
  const fuseau = useParametres().data?.fuseauHoraire;
  const comptes = useComptes();
  const comptages = useComptagesEnAttente();
  const [selection, setSelection] = useState<Selection>("principale");
  const [operation, setOperation] = useState<{ type: TypeOperation; utilisateurId?: number } | null>(null);
  const donnees = comptes.data;
  const caissiers = donnees?.caissiers ?? [];
  const enAttente = comptages.data ?? [];
  const choisi =
    typeof selection === "number" ? caissiers.find((c) => c.utilisateurId === selection) : undefined;

  return (
    <div className="tresorerie-ecran">
      <nav className="tresorerie-comptes" aria-label="Comptes">
        <span className="tresorerie-comptes-titre">Comptes</span>
        {donnees && (
          <>
            <LigneCompte
              nom="Caisse principale"
              detail={
                donnees.aValiderNombre > 0
                  ? `${donnees.aValiderNombre} versement${donnees.aValiderNombre > 1 ? "s" : ""} à valider`
                  : "Alimentations, retraits, versements"
              }
              solde={donnees.soldePrincipale}
              courant={selection === "principale"}
              onChoisir={() => setSelection("principale")}
            />
            {caissiers.map((c) => (
              <LigneCompte
                key={c.utilisateurId}
                nom={c.nom}
                detail={detailCaissier(c, enAttente)}
                solde={c.solde}
                courant={selection === c.utilisateurId}
                inactif={!c.actif}
                onChoisir={() => setSelection(c.utilisateurId)}
              />
            ))}
          </>
        )}
      </nav>

      <main className="tresorerie-principal">
        {comptes.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(comptes.error)}
          </p>
        )}
        {donnees &&
          (choisi ? (
            <EnteteCaissier
              compte={choisi}
              comptages={enAttente.filter((s) => s.caissierId === choisi.utilisateurId)}
              fuseau={fuseau}
              onOperation={(type) => setOperation({ type, utilisateurId: choisi.utilisateurId })}
            />
          ) : (
            <EntetePrincipale
              solde={donnees.soldePrincipale}
              aValiderNombre={donnees.aValiderNombre}
              fuseau={fuseau}
              onOperation={(type) => setOperation({ type })}
            />
          ))}
        <JournalTresorerie
          key={String(selection)}
          caissiers={caissiers}
          utilisateurInitial={choisi?.utilisateurId}
          fuseau={fuseau}
        />
      </main>

      {operation && donnees && (
        <OperationTresorerieDialogue
          type={operation.type}
          utilisateurId={operation.utilisateurId}
          soldePrincipale={donnees.soldePrincipale}
          caissiers={caissiers}
          onFermer={() => setOperation(null)}
        />
      )}
    </div>
  );
}

/** Ligne de détail d'un caissier : versements en cours, caisse ouverte ou comptage en attente. */
function detailCaissier(c: Compte, comptages: Session[]): string {
  const parties: string[] = [];
  if (c.envoyesEnTransitNombre > 0) parties.push(`${decimal(c.envoyesEnTransitMontant)} en transit`);
  if (c.recusEnTransitNombre > 0) parties.push(`${decimal(c.recusEnTransitMontant)} à valider`);
  if (comptages.some((s) => s.caissierId === c.utilisateurId)) parties.push("comptage en attente");
  if (c.sessionOuverteCaisse) parties.push(`${c.sessionOuverteCaisse} ouverte`);
  if (!c.actif) parties.push("désactivé");
  return parties.length > 0 ? parties.join(" · ") : "Aucun mouvement en cours";
}

function LigneCompte({
  nom,
  detail,
  solde,
  courant,
  inactif,
  onChoisir,
}: {
  nom: string;
  detail: string;
  solde: number;
  courant: boolean;
  inactif?: boolean;
  onChoisir: () => void;
}) {
  return (
    <button
      type="button"
      className={`tresorerie-compte${inactif ? " inactif" : ""}`}
      aria-current={courant ? "true" : undefined}
      onClick={onChoisir}
    >
      <span className="tresorerie-compte-texte">
        <span className="tresorerie-compte-nom">{nom}</span>
        <span className="tresorerie-compte-detail">{detail}</span>
      </span>
      <strong>{decimal(solde)}</strong>
    </button>
  );
}

function EntetePrincipale({
  solde,
  aValiderNombre,
  fuseau,
  onOperation,
}: {
  solde: number;
  aValiderNombre: number;
  fuseau: string | undefined;
  onOperation: (type: TypeOperation) => void;
}) {
  const [voir, setVoir] = useState(false);
  const aValider = useAValiderPrincipale();
  const liste = aValider.data ?? [];
  return (
    <>
      <div className="tresorerie-entete">
        <div className="tresorerie-solde">
          <span>Caisse principale</span>
          <SoldeDh montant={solde} />
        </div>
        <button type="button" className="bouton-compact" onClick={() => onOperation("retirer")}>
          Retirer
        </button>
        <button type="button" className="bouton-compact" onClick={() => onOperation("verser")}>
          Verser à un caissier
        </button>
        <button type="button" className="bouton-compact plein" onClick={() => onOperation("alimenter")}>
          Alimenter
        </button>
      </div>
      {aValiderNombre > 0 && (
        <div className="bandeau-info">
          <span className="bandeau-info-texte">
            <strong>
              {aValiderNombre} versement{aValiderNombre > 1 ? "s" : ""} à valider
            </strong>{" "}
            <span className="texte-gris">
              {liste.map((v) => ` · ${v.caissier} + ${decimal(v.montant)}`).join("")}
            </span>
          </span>
          <button type="button" className="bouton-compact plein" onClick={() => setVoir(!voir)}>
            {voir ? "Masquer" : "Voir et valider"}
          </button>
        </div>
      )}
      {voir && aValiderNombre > 0 && (
        <ul className="liste-a-valider">
          {liste.map((v) => (
            <VersementAValider key={v.id} versement={v} emetteur={v.caissier} fuseau={fuseau} />
          ))}
        </ul>
      )}
    </>
  );
}

function EnteteCaissier({
  compte,
  comptages,
  fuseau,
  onOperation,
}: {
  compte: Compte;
  comptages: Session[];
  fuseau: string | undefined;
  onOperation: (type: TypeOperation) => void;
}) {
  const [aCompter, setACompter] = useState<Session | null>(null);
  return (
    <>
      <div className="tresorerie-entete">
        <div className="tresorerie-solde">
          <span>
            Solde dû par {compte.nom}
            {compte.sessionOuverteCaisse ? ` · ${compte.sessionOuverteCaisse} ouverte, ventes comprises` : ""}
          </span>
          <SoldeDh montant={compte.solde} />
        </div>
        <button
          type="button"
          className="bouton-compact"
          disabled={compte.solde <= 0}
          onClick={() => onOperation("ajuster")}
        >
          Ajuster / passer en perte
        </button>
        <button
          type="button"
          className="bouton-compact plein"
          disabled={!compte.actif}
          onClick={() => onOperation("verser")}
        >
          Verser à {compte.nom}
        </button>
      </div>
      {comptages.map((s) => (
        <div key={s.id} className="bandeau-info bandeau-attention">
          <span className="bandeau-info-texte">
            <strong>Comptage en attente</strong>{" "}
            <span>
              · {s.caisseNom} clôturée d'office à {heure(s.fermeture ?? s.ouverture, fuseau)}
            </span>
          </span>
          <button type="button" className="bouton-compact plein" onClick={() => setACompter(s)}>
            Terminer le comptage
          </button>
        </div>
      ))}
      {aCompter && (
        <ComptageDialogue
          session={aCompter}
          caissier={compte.nom}
          fuseau={fuseau}
          onFermer={() => setACompter(null)}
        />
      )}
    </>
  );
}

const STATUTS: Record<StatutJournal, string> = {
  VALIDE: "Validé",
  AUTO_VALIDE: "Auto-validé",
  EN_TRANSIT: "En transit / à valider",
  REFUSE: "Refusé",
  ANNULE: "Annulé",
};

const COLONNES: [string, boolean][] = [
  ["Date", false],
  ["Opération", false],
  ["Utilisateur", false],
  ["Montant", true],
  ["Écart", true],
  ["Statut", false],
  ["Solde CP après", true],
  ["Solde caissier après", true],
];

/** Libellé court de l'opération, et son complément en gris (caisse, motif). */
function operation(o: Schemas["Operation"]): [string, string] {
  const complement = [o.caisse, o.motif].filter(Boolean).join(" · ");
  switch (o.type) {
    case "ALIMENTATION":
      return ["Alimentation", complement];
    case "RETRAIT":
      return ["Retrait", complement];
    case "VERSEMENT_ENVOYE":
      if (!o.versLaPrincipale) return ["Versement à un caissier", complement];
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

/** Journal de la caisse principale : toutes les opérations, filtres compacts, paginé. */
function JournalTresorerie({
  caissiers,
  utilisateurInitial,
  fuseau,
}: {
  caissiers: Compte[];
  utilisateurInitial: number | undefined;
  fuseau: string | undefined;
}) {
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
  const journal = useJournalTresorerie(filtre);
  const lignes = journal.data?.pages.flatMap((p) => p.contenu) ?? [];

  const date = (iso: string) => {
    const j = new Intl.DateTimeFormat("en-CA", { timeZone: fuseau }).format(new Date(iso));
    const prefixe = j === jourCourant ? "Auj." : `${j.slice(8, 10)}/${j.slice(5, 7)}`;
    return `${prefixe} ${heure(iso, fuseau)}`;
  };

  return (
    <section className="carte-journal" aria-label="Journal de la caisse principale">
      <div className="carte-journal-tete">
        <h2>Journal de la caisse principale</h2>
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
          {COLONNES.map(([titre, droite]) => (
            <span key={titre} role="columnheader" className={droite ? "a-droite" : undefined}>
              {titre}
            </span>
          ))}
        </div>
        {journal.isSuccess && lignes.length === 0 && <p className="vide">Aucune opération.</p>}
        {lignes.map((o) => {
          const [libelle, complement] = operation(o);
          return (
            <div key={o.id} className="journal-ligne" role="row">
              <span role="cell" className="texte-gris">
                {date(o.le)}
              </span>
              <span role="cell">
                <span className="journal-operation">{libelle}</span>
                {complement && <span className="texte-gris"> · {complement}</span>}
              </span>
              <span role="cell">{o.caissier ?? o.auteur}</span>
              <span role="cell" className={`a-droite montant-signe ${o.montant < 0 ? "negatif" : "positif"}`}>
                {signe(o.montant)}
              </span>
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
              <span role="cell" className="a-droite">
                {decimal(o.soldePrincipaleApres)}
              </span>
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
