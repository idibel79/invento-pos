import { useEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from "react";

const entier = new Intl.NumberFormat("fr-FR", { maximumFractionDigits: 0 });

/** Précision du curseur : nombre de crans sur toute la piste. */
const CRANS = 1000;
/** Graduations proposées sous la piste, gardées si elles tombent entre les bornes. */
const GRADUATIONS = [50, 100, 250, 500];

interface Props {
  /** Bornes du curseur, en dirhams entiers (prix le plus bas et le plus haut du catalogue). */
  min: number;
  max: number;
  /** Plage appliquée ; null de chaque côté : pas de limite. */
  bas: number | null;
  haut: number | null;
  onChanger: (bas: number | null, haut: number | null) => void;
}

/**
 * Bouton « Prix » de la barre des filtres : il affiche la plage appliquée et ouvre un panneau avec
 * un curseur à deux poignées et deux champs Min / Max. La liste ne change qu'à « Appliquer ».
 */
export function FiltrePrix({ min, max, bas, haut, onChanger }: Props) {
  const [ouvert, setOuvert] = useState(false);
  const conteneur = useRef<HTMLDivElement>(null);
  const declencheur = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!ouvert) return;
    const clic = (e: PointerEvent) => {
      if (!conteneur.current?.contains(e.target as Node)) setOuvert(false);
    };
    const touche = (e: globalThis.KeyboardEvent) => {
      if (e.key !== "Escape") return;
      setOuvert(false);
      declencheur.current?.focus();
    };
    document.addEventListener("pointerdown", clic);
    document.addEventListener("keydown", touche);
    return () => {
      document.removeEventListener("pointerdown", clic);
      document.removeEventListener("keydown", touche);
    };
  }, [ouvert]);

  function fermer(nouveauBas: number | null, nouveauHaut: number | null) {
    onChanger(nouveauBas, nouveauHaut);
    setOuvert(false);
    declencheur.current?.focus();
  }

  const actif = bas !== null || haut !== null;
  const libelle = actif ? `${entier.format(bas ?? min)} – ${entier.format(haut ?? max)} DH` : "Tous";
  return (
    <div className="filtre-prix" ref={conteneur}>
      <button
        ref={declencheur}
        type="button"
        className={`filtre filtre-bouton${actif ? " actif" : ""}`}
        aria-haspopup="dialog"
        aria-expanded={ouvert}
        onClick={() => setOuvert((o) => !o)}
      >
        <span>Prix :</span>
        <strong>{libelle}</strong>
        <svg
          width="14"
          height="14"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          aria-hidden="true"
        >
          <path d={ouvert ? "M18 15l-6-6-6 6" : "M6 9l6 6 6-6"} strokeLinecap="round" />
        </svg>
      </button>
      {ouvert && (
        <PanneauPrix
          min={min}
          max={max}
          bas={bas ?? min}
          haut={haut ?? max}
          onAppliquer={(b, h) => fermer(b <= min ? null : b, h >= max ? null : h)}
          onTous={() => fermer(null, null)}
        />
      )}
    </div>
  );
}

/** Le curseur suit une échelle resserrée sur les petits prix, où se trouve l'essentiel du catalogue. */
function versPosition(valeur: number, min: number, max: number): number {
  const etendue = Math.max(max - min, 1);
  return Math.round(CRANS * Math.sqrt(Math.min(Math.max(valeur - min, 0), etendue) / etendue));
}

function versValeur(position: number, min: number, max: number): number {
  return Math.round(min + (max - min) * (position / CRANS) ** 2);
}

function PanneauPrix({
  min,
  max,
  bas: basInitial,
  haut: hautInitial,
  onAppliquer,
  onTous,
}: {
  min: number;
  max: number;
  bas: number;
  haut: number;
  onAppliquer: (bas: number, haut: number) => void;
  onTous: () => void;
}) {
  // Brouillon : la liste ne bouge pas pendant le réglage.
  const [bas, setBas] = useState(basInitial);
  const [haut, setHaut] = useState(hautInitial);
  const [texteBas, setTexteBas] = useState(String(basInitial));
  const [texteHaut, setTexteHaut] = useState(String(hautInitial));

  const positionBas = versPosition(bas, min, max);
  const positionHaut = versPosition(haut, min, max);
  const graduations = [min, ...GRADUATIONS.filter((g) => g > min && g < max), max];

  function glisserBas(position: number) {
    const v = Math.min(versValeur(position, min, max), haut);
    setBas(v);
    setTexteBas(String(v));
  }
  function glisserHaut(position: number) {
    const v = Math.max(versValeur(position, min, max), bas);
    setHaut(v);
    setTexteHaut(String(v));
  }
  function saisir(texte: string, cote: "bas" | "haut") {
    (cote === "bas" ? setTexteBas : setTexteHaut)(texte);
    const v = Number(texte.replace(",", "."));
    if (texte.trim() === "" || !Number.isFinite(v)) return;
    const borne = Math.min(Math.max(Math.round(v), min), max);
    (cote === "bas" ? setBas : setHaut)(borne);
  }
  function appliquer() {
    onAppliquer(Math.min(bas, haut), Math.max(bas, haut));
  }
  function valider(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") appliquer();
  }

  return (
    <div className="panneau-prix" role="dialog" aria-label="Filtrer par prix">
      <strong className="panneau-prix-titre">Prix de vente TTC</strong>
      <div
        className="curseur-double"
        style={
          {
            "--bas": `${positionBas / (CRANS / 100)}%`,
            "--haut": `${positionHaut / (CRANS / 100)}%`,
          } as CSSProperties
        }
      >
        <input
          type="range"
          min={0}
          max={CRANS}
          value={positionBas}
          // Poignées confondues en haut : la poignée basse passe dessus pour pouvoir redescendre.
          className={positionBas > CRANS / 2 ? "au-dessus" : undefined}
          aria-label="Prix minimum"
          aria-valuetext={`${entier.format(bas)} DH`}
          onChange={(e) => glisserBas(Number(e.target.value))}
        />
        <input
          type="range"
          min={0}
          max={CRANS}
          value={positionHaut}
          aria-label="Prix maximum"
          aria-valuetext={`${entier.format(haut)} DH`}
          onChange={(e) => glisserHaut(Number(e.target.value))}
        />
      </div>
      <div className="curseur-graduations" aria-hidden="true">
        {graduations.map((g) => (
          <span key={g} style={{ left: `${versPosition(g, min, max) / (CRANS / 100)}%` }}>
            {entier.format(g)}
          </span>
        ))}
      </div>
      <div className="panneau-prix-champs">
        <label className="champ-prix">
          <span>Minimum</span>
          <span className="champ-prix-saisie">
            <input
              inputMode="numeric"
              value={texteBas}
              onChange={(e) => saisir(e.target.value, "bas")}
              onKeyDown={valider}
            />
            DH
          </span>
        </label>
        <span className="panneau-prix-tiret" aria-hidden="true">
          –
        </span>
        <label className="champ-prix">
          <span>Maximum</span>
          <span className="champ-prix-saisie">
            <input
              inputMode="numeric"
              value={texteHaut}
              onChange={(e) => saisir(e.target.value, "haut")}
              onKeyDown={valider}
            />
            DH
          </span>
        </label>
      </div>
      <div className="panneau-prix-actions">
        <button type="button" className="bouton-petit" onClick={onTous}>
          Tous les prix
        </button>
        <button type="button" className="bouton-petit bouton-appliquer" onClick={appliquer}>
          Appliquer
        </button>
      </div>
    </div>
  );
}
