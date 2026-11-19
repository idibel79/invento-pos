import { useEffect, useRef, useState } from "react";

const MOIS = [
  "janvier",
  "février",
  "mars",
  "avril",
  "mai",
  "juin",
  "juillet",
  "août",
  "septembre",
  "octobre",
  "novembre",
  "décembre",
];
const JOURS = ["lu", "ma", "me", "je", "ve", "sa", "di"];

/** AAAA-MM-JJ d'une date du calendrier (année, mois de 0 à 11, jour). */
function iso(annee: number, mois: number, jour: number): string {
  return `${annee}-${String(mois + 1).padStart(2, "0")}-${String(jour).padStart(2, "0")}`;
}

/** Jour (AAAA-MM-JJ) décalé de n jours. */
function decaler(jour: string, jours: number): string {
  const date = new Date(`${jour}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + jours);
  return date.toISOString().slice(0, 10);
}

/** Filtre « Jour : ‹ [calendrier] › » : jour précédent, choix au calendrier, jour suivant (jusqu'à aujourd'hui). */
export function FiltreJour({ jour, aujourdhui, onChange }: Omit<Props, "className">) {
  return (
    <div className="filtre filtre-jour">
      <span>Jour :</span>
      <button
        type="button"
        className="bouton-jour"
        aria-label="Jour précédent"
        disabled={jour === null}
        onClick={() => jour && onChange(decaler(jour, -1))}
      >
        ‹
      </button>
      <ChoixJour jour={jour} aujourdhui={aujourdhui} onChange={onChange} />
      <button
        type="button"
        className="bouton-jour"
        aria-label="Jour suivant"
        disabled={jour === null || jour >= aujourdhui}
        onClick={() => jour && onChange(decaler(jour, 1))}
      >
        ›
      </button>
    </div>
  );
}

interface Props {
  /** Jour choisi (AAAA-MM-JJ), ou null pour tous les jours. */
  jour: string | null;
  /** Aujourd'hui dans le fuseau de la boutique : borne haute et bouton Aujourd'hui. */
  aujourdhui: string;
  /** null : tous les jours. */
  onChange: (jour: string | null) => void;
  className?: string;
}

/**
 * Choix d'un jour : remplace le calendrier du navigateur, dont les boutons ne se personnalisent pas.
 * Pied de calendrier : Tous (bleu) et Aujourd'hui (vert).
 */
export function ChoixJour({ jour, aujourdhui, onChange, className }: Props) {
  const [ouvert, setOuvert] = useState(false);
  const reference = jour ?? aujourdhui;
  const [vue, setVue] = useState({
    annee: Number(reference.slice(0, 4)),
    mois: Number(reference.slice(5, 7)) - 1,
  });
  const racine = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!ouvert) return;
    const fermerDehors = (e: MouseEvent) => {
      if (!racine.current?.contains(e.target as Node)) setOuvert(false);
    };
    const fermerEchap = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOuvert(false);
    };
    document.addEventListener("mousedown", fermerDehors);
    document.addEventListener("keydown", fermerEchap);
    return () => {
      document.removeEventListener("mousedown", fermerDehors);
      document.removeEventListener("keydown", fermerEchap);
    };
  }, [ouvert]);

  const ouvrir = () => {
    setVue({ annee: Number(reference.slice(0, 4)), mois: Number(reference.slice(5, 7)) - 1 });
    setOuvert((o) => !o);
  };
  const choisir = (j: string | null) => {
    onChange(j);
    setOuvert(false);
  };
  const decalerMois = (pas: number) => {
    const d = new Date(vue.annee, vue.mois + pas, 1);
    setVue({ annee: d.getFullYear(), mois: d.getMonth() });
  };

  // Lundi en tête de semaine.
  const decalage = (new Date(vue.annee, vue.mois, 1).getDay() + 6) % 7;
  const nombreJours = new Date(vue.annee, vue.mois + 1, 0).getDate();
  const cases: (number | null)[] = [
    ...Array.from({ length: decalage }, () => null),
    ...Array.from({ length: nombreJours }, (_, i) => i + 1),
  ];
  // Pas de mois suivant quand il commence après aujourd'hui.
  const suivant = new Date(vue.annee, vue.mois + 1, 1);
  const moisSuivantFutur = iso(suivant.getFullYear(), suivant.getMonth(), 1) > aujourdhui;

  return (
    <div className={`choix-jour ${className ?? ""}`} ref={racine}>
      <button
        type="button"
        className="choix-jour-bouton"
        aria-haspopup="dialog"
        aria-expanded={ouvert}
        aria-label="Jour"
        onClick={ouvrir}
      >
        {jour ? `${jour.slice(8, 10)}/${jour.slice(5, 7)}/${jour.slice(0, 4)}` : "Tous les jours"}
        <svg aria-hidden="true" className="choix-jour-icone" viewBox="0 0 16 16" width="14" height="14">
          <rect
            x="2"
            y="3"
            width="12"
            height="11"
            rx="1.5"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.4"
          />
          <path d="M2 6.5h12M5 1.5v3M11 1.5v3" stroke="currentColor" strokeWidth="1.4" />
        </svg>
      </button>
      {ouvert && (
        <div className="choix-jour-calendrier" role="dialog" aria-label="Choisir un jour">
          <div className="choix-jour-tete">
            <button type="button" aria-label="Mois précédent" onClick={() => decalerMois(-1)}>
              ‹
            </button>
            <span>
              {MOIS[vue.mois]} {vue.annee}
            </span>
            <button
              type="button"
              aria-label="Mois suivant"
              disabled={moisSuivantFutur}
              onClick={() => decalerMois(1)}
            >
              ›
            </button>
          </div>
          <div className="choix-jour-grille">
            {JOURS.map((j) => (
              <span key={j} className="choix-jour-nom">
                {j}
              </span>
            ))}
            {cases.map((n, i) => {
              if (n == null) return <span key={`v${i}`} />;
              const valeur = iso(vue.annee, vue.mois, n);
              const classes = [
                "choix-jour-case",
                valeur === jour ? "choisi" : "",
                valeur === aujourdhui ? "aujourdhui" : "",
              ].join(" ");
              return (
                <button
                  key={valeur}
                  type="button"
                  className={classes}
                  disabled={valeur > aujourdhui}
                  aria-pressed={valeur === jour}
                  onClick={() => choisir(valeur)}
                >
                  {n}
                </button>
              );
            })}
          </div>
          <div className="choix-jour-pied">
            <button type="button" className="choix-jour-tous" onClick={() => choisir(null)}>
              Tous
            </button>
            <button type="button" className="choix-jour-aujourdhui" onClick={() => choisir(aujourdhui)}>
              Aujourd'hui
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
