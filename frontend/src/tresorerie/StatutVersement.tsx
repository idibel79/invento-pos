import type { Schemas } from "../api/client";

type Statut = Schemas["VersementDto"]["statut"];

const LIBELLES: Record<Exclude<Statut, "EN_TRANSIT">, { texte: string; classe: string }> = {
  VALIDE: { texte: "Validé", classe: "statut-ok" },
  REFUSE: { texte: "Refusé", classe: "statut-danger" },
  ANNULE: { texte: "Annulé", classe: "statut-gris" },
};

interface Props {
  /** Absent : opération immédiate, validée d'office. */
  statut: Statut | null | undefined;
  autoValide?: boolean;
  /** Sens du versement : en transit vers la caisse principale, ou à valider par le caissier. */
  versLaPrincipale?: boolean | null;
}

/**
 * Pastille du statut d'un versement (maquette B) : En transit en bleu vers la caisse principale, À valider
 * en ambre vers un caissier, Auto-validé en ambre, Validé vert, Refusé rouge, Annulé gris.
 */
export function StatutVersement({ statut, autoValide, versLaPrincipale }: Props) {
  if (autoValide) return <span className="statut statut-attention">Auto-validé</span>;
  if (statut === "EN_TRANSIT") {
    return versLaPrincipale === false ? (
      <span className="statut statut-attention">À valider</span>
    ) : (
      <span className="statut statut-info">En transit</span>
    );
  }
  const { texte, classe } = LIBELLES[statut ?? "VALIDE"];
  return <span className={`statut ${classe}`}>{texte}</span>;
}
