import { useMonCompte } from "../tresorerie/requetes";

/**
 * Bouton « Administration » de la caisse et de l'ouverture, avec la pastille des versements à valider
 * dans Mon compte (qui est désormais une rubrique de l'administration).
 */
export function BoutonAdministration({ classe, onClick }: { classe: string; onClick: () => void }) {
  const aValider = useMonCompte().data?.aValider.length ?? 0;
  return (
    <button
      type="button"
      className={`${classe} bouton-mon-compte`}
      onClick={onClick}
      aria-label={`Administration${aValider > 0 ? `, ${aValider} versement${aValider > 1 ? "s" : ""} à valider` : ""}`}
    >
      Administration
      {aValider > 0 && <span className="pastille-nombre">{aValider}</span>}
    </button>
  );
}
