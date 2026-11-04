import { lireMontant } from "./format";

export type Touche = "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" | "," | "retour";

/** Saisie après une touche du pavé ; une touche qui rendrait le montant invalide est ignorée. */
export function saisirTouche(saisie: string, touche: Touche): string {
  if (touche === "retour") return saisie.slice(0, -1);
  if (touche === ",") {
    if (saisie.includes(",") || saisie.includes(".")) return saisie;
    return (saisie === "" ? "0" : saisie) + ",";
  }
  const suivante = saisie === "0" ? touche : saisie + touche;
  return lireMontant(suivante) === null ? saisie : suivante;
}

interface Props {
  onTouche: (touche: Touche) => void;
  onEffacer: () => void;
  onExact: () => void;
}

const CHIFFRES: Touche[] = ["7", "8", "9", "4", "5", "6", "1", "2", "3"];

/** Pavé numérique tactile pour un montant (le clavier physique reste utilisable). */
export function PaveNumerique({ onTouche, onEffacer, onExact }: Props) {
  return (
    <div className="pave-numerique" role="group" aria-label="Pavé numérique">
      {CHIFFRES.map((c) => (
        <button key={c} type="button" onClick={() => onTouche(c)}>
          {c}
        </button>
      ))}
      <button type="button" className="pave-zero" onClick={() => onTouche("0")}>
        0
      </button>
      <button type="button" onClick={() => onTouche(",")} aria-label="Virgule">
        ,
      </button>
      <button
        type="button"
        className="pave-action pave-retour"
        onClick={() => onTouche("retour")}
        aria-label="Effacer le dernier chiffre"
      >
        <svg
          width="24"
          height="24"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M21 5H9l-6 7 6 7h12z" />
          <path d="M17 9l-6 6M11 9l6 6" />
        </svg>
      </button>
      <button type="button" className="pave-action pave-effacer" onClick={onEffacer}>
        Effacer
      </button>
      <button type="button" className="pave-action pave-exact" onClick={onExact}>
        Montant exact
      </button>
    </div>
  );
}
