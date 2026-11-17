import { dh } from "../ui/format";

/** Répartition du solde dû à la caisse principale, affichée sous le montant : dû en espèces et dû en TPE. */
export function RepartitionDu({ especes, tpe }: { especes: number; tpe: number }) {
  return (
    <span className="tresorerie-repartition">
      dont espèces <strong>{dh(especes)}</strong> · TPE <strong>{dh(tpe)}</strong>
    </span>
  );
}
