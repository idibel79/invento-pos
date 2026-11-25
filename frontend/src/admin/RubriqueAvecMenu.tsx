import { type ReactNode } from "react";
import { naviguer, slug, useAdresse } from "../ui/adresse";

export type EntreeMenu = { nom: string; detail: string; ecran: () => ReactNode };

type Props = { titre: string; onglet: string; entrees: EntreeMenu[] };

/**
 * Rubrique d'administration disposée comme la Caisse principale : menu à gauche, écran choisi à
 * droite. L'entrée choisie est gardée dans l'adresse (`#/administration/<onglet>/<entrée>`).
 */
export function RubriqueAvecMenu({ titre, onglet, entrees }: Props) {
  const demandee = useAdresse()[2];
  const choisie = Math.max(
    0,
    entrees.findIndex((e) => slug(e.nom) === demandee),
  );
  const choisir = (e: EntreeMenu) => naviguer(["administration", onglet, slug(e.nom)]);

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
            onClick={() => choisir(e)}
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
