import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useParametres } from "../caisse/requetes";
import { JournalTresorerie } from "../tresorerie/JournalTresorerie";
import { MonCompte } from "../tresorerie/MonCompte";
import {
  useAValiderPrincipale,
  useComptagesEnAttente,
  useComptes,
  useMonCompte,
} from "../tresorerie/requetes";
import { VersementAValider } from "../tresorerie/VersementAValider";
import { decimal, heure } from "../ui/format";
import { ComptageDialogue } from "./ComptageDialogue";
import { OperationTresorerieDialogue, type TypeOperation } from "./OperationTresorerieDialogue";

type Selection = "principale" | "monCompte" | number;
type Compte = Schemas["CompteCaissierDto"];
type Session = Schemas["SessionCaisseDto"];

function SoldeDh({ montant }: { montant: number }) {
  return (
    <strong className="tresorerie-solde-montant">
      {decimal(montant)} <span>DH</span>
    </strong>
  );
}

/**
 * Rubrique Comptes de l'admin, maquette B : comptes dans une colonne à gauche (caisse principale, Mon
 * compte puis chaque caissier), à droite le compte choisi (solde et opérations), les versements à
 * valider et le journal de toutes les opérations ; Mon compte affiche l'écran du même nom.
 */
export function CaissePrincipale() {
  const fuseau = useParametres().data?.fuseauHoraire;
  const comptes = useComptes();
  const monCompte = useMonCompte().data;
  const monAValider = monCompte?.aValider.length ?? 0;
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
            <LigneCompte
              nom="Mon compte"
              detail={
                monAValider > 0
                  ? `${monAValider} versement${monAValider > 1 ? "s" : ""} à valider`
                  : "Solde dû, versements et ventes"
              }
              solde={monCompte?.solde ?? 0}
              courant={selection === "monCompte"}
              onChoisir={() => setSelection("monCompte")}
            />
            {/* L'administrateur a déjà son entrée « Mon compte ». */}
            {caissiers
              .filter((c) => c.role !== "ADMIN")
              .map((c) => (
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
        {selection === "monCompte" && <MonCompte />}
        {donnees &&
          selection !== "monCompte" &&
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
        {selection !== "monCompte" && (
          <JournalTresorerie
            key={String(selection)}
            caissiers={caissiers}
            utilisateurInitial={choisi?.utilisateurId}
            fuseau={fuseau}
          />
        )}
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
