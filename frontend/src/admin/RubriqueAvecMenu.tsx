import { type ReactNode, useState } from "react";

export type EntreeMenu = { nom: string; detail: string; ecran: () => ReactNode };

/** Rubrique d'administration disposée comme la Caisse principale : menu à gauche, écran choisi à droite. */
export function RubriqueAvecMenu({ titre, entrees }: { titre: string; entrees: EntreeMenu[] }) {
  const [choisie, setChoisie] = useState(0);

  return (
    <div className="tresorerie-ecran">
      <nav className="tresorerie-comptes" aria-label={titre}>
        <span className="tresorerie-comptes-titre">{titre}</span>
        {entrees.map((e, i) => (
          <button
            key={e.nom}
            type="button"
            className="tresorerie-compte"
            aria-current={choisie === i ? "true" : undefined}
            onClick={() => setChoisie(i)}
          >
            <span className="tresorerie-compte-texte">
              <span className="tresorerie-compte-nom">{e.nom}</span>
              <span className="tresorerie-compte-detail">{e.detail}</span>
            </span>
          </button>
        ))}
      </nav>
      <div className="tresorerie-principal">{entrees[choisie]?.ecran()}</div>
    </div>
  );
}
