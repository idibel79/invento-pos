import { useState } from "react";
import { DetailVente, libellePaiement } from "../admin/HistoriqueVentes";
import { useHistoriqueVentes } from "../admin/requetes";
import { messageErreur } from "../api/erreurs";
import { useUtilisateur } from "../auth/AuthContext";
import { useParametres } from "../caisse/requetes";
import { Dialogue } from "../ui/Dialogue";
import { dateHeure, dh } from "../ui/format";
import type { Client } from "./requetes";

/**
 * Ventes validées d'un client, de la plus récente à la plus ancienne ; une vente s'ouvre sur son détail,
 * d'où l'on réimprime le ticket et, pour une entreprise, la facture A4. Hors admin, le serveur ne renvoie
 * que les ventes de l'utilisateur.
 */
export function VentesClientDialogue({ client, onFermer }: { client: Client; onFermer: () => void }) {
  const admin = useUtilisateur().role === "ADMIN";
  const fuseau = useParametres().data?.fuseauHoraire;
  const ventes = useHistoriqueVentes({ clientId: client.id });
  const [ouverte, setOuverte] = useState<number>();
  const liste = ventes.data?.pages.flatMap((p) => p.contenu) ?? [];
  const entreprise = client.type === "ENTREPRISE";

  return (
    <Dialogue
      titre={`Ventes · ${client.nom}`}
      sousTitre={admin ? undefined : "Vos ventes à ce client"}
      onFermer={onFermer}
      largeur={820}
    >
      <div className="formulaire releve-popup">
        {ventes.isPending && <p className="vide">Chargement…</p>}
        {ventes.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(ventes.error)}
          </p>
        )}
        {ventes.isSuccess && liste.length === 0 && <p className="vide">Aucune vente pour ce client.</p>}
        {liste.length > 0 && (
          <div className="releve-defilement">
            <table className="releve-tableau">
              <thead>
                <tr>
                  <th scope="col">Date</th>
                  <th scope="col">Ticket</th>
                  <th scope="col">Vendeur</th>
                  <th scope="col">Paiement</th>
                  <th scope="col" className="a-droite">
                    Total TTC
                  </th>
                  <th scope="col">
                    <span className="visuellement-cache">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {liste.map((v) => (
                  <tr key={v.id}>
                    <td>{dateHeure(v.le, fuseau)}</td>
                    <td>{v.numero}</td>
                    <td>{v.vendeur}</td>
                    <td>{v.mode === "CREDIT" ? "Crédit" : libellePaiement(v)}</td>
                    <td className="a-droite">{dh(v.totalTtc)}</td>
                    <td>
                      <button type="button" className="lien-action" onClick={() => setOuverte(v.id)}>
                        {entreprise ? "Ticket / Facture A4" : "Ticket"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {ventes.hasNextPage && (
              <button
                type="button"
                className="tableau-suite"
                onClick={() => void ventes.fetchNextPage()}
                disabled={ventes.isFetchingNextPage}
              >
                Afficher plus de ventes
              </button>
            )}
          </div>
        )}
        <div className="rangee-boutons">
          <span className="texte-secondaire releve-aide">
            Ouvrez une vente pour voir son détail et la réimprimer.
          </span>
          <button type="button" className="bouton bouton-plein" onClick={onFermer}>
            Fermer
          </button>
        </div>
      </div>
      {ouverte !== undefined && (
        <DetailVente
          id={ouverte}
          fuseau={fuseau}
          onFermer={() => setOuverte(undefined)}
          factureA4={entreprise}
        />
      )}
    </Dialogue>
  );
}
