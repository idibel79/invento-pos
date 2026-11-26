import { arrondi } from "../caisse/cloture";
import { decimal } from "../ui/format";

interface Props {
  tiroirEspeces: number;
  tiroirTpe: number;
  compteEspeces: number;
  compteTpe: number;
}

/**
 * Caisse ouverte : où est l'argent dû, par mode. Le tiroir compte les ventes de la session ; les montants sont
 * les plafonds de la fenêtre Verser.
 */
export function TiroirEtCompte({ tiroirEspeces, tiroirTpe, compteEspeces, compteTpe }: Props) {
  return (
    <table className="tiroir-et-compte">
      <caption className="visuellement-cache">Argent dû, dans le tiroir et sur moi, par mode (DH)</caption>
      <thead>
        <tr>
          <td />
          <th scope="col">Dans le tiroir</th>
          <th scope="col">Sur moi</th>
          <th scope="col">Total</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <th scope="row">Espèces</th>
          <td>{decimal(tiroirEspeces)}</td>
          <td>{decimal(compteEspeces)}</td>
          <td className="total">{decimal(arrondi(tiroirEspeces + compteEspeces))}</td>
        </tr>
        <tr>
          <th scope="row">Tickets TPE</th>
          <td>{decimal(tiroirTpe)}</td>
          <td>{decimal(compteTpe)}</td>
          <td className="total">{decimal(arrondi(tiroirTpe + compteTpe))}</td>
        </tr>
      </tbody>
    </table>
  );
}
