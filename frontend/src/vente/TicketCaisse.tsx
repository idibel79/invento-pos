import { createPortal } from "react-dom";
import type { Schemas } from "../api/client";
import { dateHeure, decimal, dh, pourcent } from "../ui/format";

type Boutique = Pick<
  Schemas["Parametres"],
  "boutiqueNom" | "boutiqueAdresse" | "boutiqueTelephone" | "boutiqueEmail" | "ticketMessage"
>;

interface Props {
  vente: Schemas["VenteDto"];
  boutique: Boutique | undefined;
  vendeur: string;
  fuseau: string | undefined;
}

/**
 * Ticket de caisse remis au client : en-tête de la boutique (Paramètres > Boutique), lignes
 * Désignation / PU / Qté / Montant, totaux, paiement, vendeur et message de pied. Tous les montants
 * viennent de la vente calculée par le serveur.
 */
export function TicketCaisse({ vente, boutique, vendeur, fuseau }: Props) {
  const t = vente.totaux;
  const paiement = vente.paiement;
  const tva = t.tva.filter((v) => v.taux > 0);

  return (
    <article className="ticket-caisse" aria-label={`Ticket ${vente.numero}`}>
      <header className="ticket-entete">
        {boutique?.boutiqueNom && <strong className="ticket-boutique">{boutique.boutiqueNom}</strong>}
        {boutique?.boutiqueAdresse && <span>{boutique.boutiqueAdresse}</span>}
        {boutique?.boutiqueTelephone && <span>Tél. : {boutique.boutiqueTelephone}</span>}
        {boutique?.boutiqueEmail && <span>{boutique.boutiqueEmail}</span>}
      </header>
      <div className="ticket-infos">
        <span>Ticket n° {vente.numero}</span>
        <span>{dateHeure(vente.finaliseeLe ?? vente.creeLe, fuseau)}</span>
      </div>
      <table className="ticket-lignes">
        <thead>
          <tr>
            <th scope="col">Désignation</th>
            <th scope="col">PU</th>
            <th scope="col">Qté</th>
            <th scope="col">MT</th>
          </tr>
        </thead>
        <tbody>
          {vente.lignes.map((l) => (
            <tr key={l.produitId}>
              <td>
                {l.designation}
                {l.remiseMontant > 0 && (
                  <span className="ticket-remise">Remise −{decimal(l.remiseMontant)}</span>
                )}
              </td>
              <td>{decimal(l.prixUnitaire)}</td>
              <td>{l.quantite}</td>
              <td>{decimal(l.montantNet)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <dl className="ticket-totaux">
        {t.totalRemises > 0 && (
          <>
            <div>
              <dt>Total</dt>
              <dd>{dh(t.sousTotal)}</dd>
            </div>
            <div>
              <dt>Remise</dt>
              <dd>−{dh(t.totalRemises)}</dd>
            </div>
          </>
        )}
        <div className="ticket-net">
          <dt>Net à payer</dt>
          <dd>{dh(t.totalTtc)}</dd>
        </div>
        {tva.map((v) => (
          <div key={v.taux} className="ticket-tva">
            <dt>
              dont TVA {pourcent(v.taux)} sur {decimal(v.totalHt)}
            </dt>
            <dd>{decimal(v.totalTva)}</dd>
          </div>
        ))}
      </dl>
      {vente.paiements.length > 1 && (
        <dl className="ticket-paiement">
          <div>
            <dt>Mode de paiement</dt>
            <dd>Espèces + carte</dd>
          </div>
          {vente.paiements.map((p) => (
            <div key={p.mode}>
              <dt>{p.mode === "CARTE" ? "Carte bancaire" : "Espèces"}</dt>
              <dd>{dh(p.montant)}</dd>
            </div>
          ))}
        </dl>
      )}
      {paiement && vente.paiements.length === 1 && (
        <dl className="ticket-paiement">
          <div>
            <dt>Mode de paiement</dt>
            <dd>{paiement.mode === "CARTE" ? "Carte bancaire" : "Espèces"}</dd>
          </div>
          {paiement.mode === "ESPECES" && paiement.montantRecu != null && (
            <>
              <div>
                <dt>Montant reçu</dt>
                <dd>{dh(paiement.montantRecu)}</dd>
              </div>
              <div>
                <dt>Rendu</dt>
                <dd>{dh(paiement.rendu ?? 0)}</dd>
              </div>
            </>
          )}
        </dl>
      )}
      <p className="ticket-vendeur">Vendeur : {vendeur}</p>
      {boutique?.ticketMessage && <p className="ticket-pied">{boutique.ticketMessage}</p>}
    </article>
  );
}

/**
 * Ticket placé hors de l'application, seul visible à l'impression (styles « @media print ») ;
 * caché à l'écran.
 */
export function TicketAImprimer(props: Props) {
  return createPortal(
    <div className="zone-impression">
      <TicketCaisse {...props} />
    </div>,
    document.body,
  );
}
