import type { Dispatch } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { dh, decimal, pourcent } from "../ui/format";
import { nombreArticles, type ActionPanier, type EtatPanier, type Remise } from "./panier";

interface Props {
  panier: EtatPanier;
  dispatch: Dispatch<ActionPanier>;
  calcul: { data: Schemas["PanierDto"] | undefined; error: Error | null; isFetching: boolean };
  onRemiseLigne: (produitId: number) => void;
  onRemiseTicket: () => void;
  onAttente: () => void;
  onEncaisser: () => void;
}

/** Ticket en cours : lignes, remises, totaux calculés par le serveur, attente et encaissement. */
export function PanierPanel({
  panier,
  dispatch,
  calcul,
  onRemiseLigne,
  onRemiseTicket,
  onAttente,
  onEncaisser,
}: Props) {
  const vide = panier.lignes.length === 0;
  // Pendant un recalcul, l'aperçu précédent reste affiché mais l'encaissement attend le nouveau.
  const apercu = vide || calcul.error ? undefined : calcul.data;
  const lignesCalculees = new Map((calcul.data?.lignes ?? []).map((l) => [l.produitId, l]));
  const autorisationManquante = apercu
    ? apercu.autorisationRequise !== "AUCUNE" && !apercu.autorisationAcquise
    : false;
  const pret = apercu !== undefined && !calcul.isFetching && apercu.stockSuffisant && !autorisationManquante;
  const articles = nombreArticles(panier);

  return (
    <aside className="panier" aria-label="Vente en cours">
      <div className="panier-tete">
        <div>
          <div className="panier-titre">
            {panier.numeroEnAttente ? `Vente reprise ${panier.numeroEnAttente}` : "Vente en cours"}
          </div>
          <div className="panier-sous-titre">
            {articles} article{articles > 1 ? "s" : ""}
            {panier.autorisePar ? ` · remise autorisée par ${panier.autorisePar}` : ""}
          </div>
        </div>
        <div className="panier-actions">
          <button type="button" className="bouton-petit" onClick={onRemiseTicket} disabled={vide}>
            {panier.remiseTicket ? `Ticket ${libelleRemise(panier.remiseTicket)}` : "Remise ticket"}
          </button>
          <button
            type="button"
            className="bouton-petit danger"
            onClick={() => dispatch({ type: "vider" })}
            disabled={vide && panier.venteEnAttenteId === null}
          >
            Vider
          </button>
        </div>
      </div>

      <div className="panier-lignes">
        {vide && <p className="vide">Scannez ou touchez un produit pour l'ajouter.</p>}
        {panier.lignes.map((ligne) => {
          const calculee = lignesCalculees.get(ligne.produitId);
          return (
            <div className="ligne" key={ligne.produitId}>
              <div className="ligne-nom">{ligne.designation}</div>
              <div className="ligne-detail">
                <div className="quantite">
                  <button
                    type="button"
                    aria-label={`Diminuer ${ligne.designation}`}
                    onClick={() =>
                      dispatch({ type: "quantite", produitId: ligne.produitId, quantite: ligne.quantite - 1 })
                    }
                  >
                    −
                  </button>
                  <span aria-label="Quantité">{ligne.quantite}</span>
                  <button
                    type="button"
                    aria-label={`Augmenter ${ligne.designation}`}
                    onClick={() =>
                      dispatch({ type: "quantite", produitId: ligne.produitId, quantite: ligne.quantite + 1 })
                    }
                  >
                    +
                  </button>
                </div>
                <span className="ligne-prix">× {decimal(calculee?.prixUnitaire ?? ligne.prixUnitaire)}</span>
                <button
                  type="button"
                  className={`bouton-remise${ligne.remise ? " active" : ""}`}
                  onClick={() => onRemiseLigne(ligne.produitId)}
                >
                  {ligne.remise ? `− ${libelleRemise(ligne.remise)}` : "% Remise"}
                </button>
                <span className="ligne-total">
                  {calculee && calculee.remiseMontant > 0 && <s>{dh(calculee.montantBrut)}</s>}
                  {calculee ? dh(calculee.montantNet) : "…"}
                </span>
              </div>
              {calculee && !calculee.stockSuffisant && (
                <span className="ligne-alerte" role="alert">
                  Stock insuffisant pour cette quantité
                </span>
              )}
            </div>
          );
        })}
      </div>

      <div className="panier-pied">
        {calcul.error && !vide && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(calcul.error)}
          </p>
        )}
        {apercu && autorisationManquante && (
          <p className="alerte alerte-danger" role="alert">
            Remise de {pourcent(apercu.tauxRemiseMax)} : autorisation{" "}
            {apercu.autorisationRequise === "ADMIN" ? "de l'admin" : "d'un responsable"} requise.
          </p>
        )}
        <div className="total-ligne">
          <span>Remises</span>
          <span>{apercu ? dh(apercu.totaux.totalRemises) : "–"}</span>
        </div>
        <div className="total-ligne">
          <span>Total HT</span>
          <span>{apercu ? dh(apercu.totaux.totalHt) : "–"}</span>
        </div>
        {(apercu?.totaux.tva ?? [])
          .filter((t) => t.taux > 0)
          .map((t) => (
            <div className="total-ligne" key={t.taux}>
              <span>TVA {pourcent(t.taux)}</span>
              <span>{dh(t.totalTva)}</span>
            </div>
          ))}
        <div className="total-ttc">
          <span>Total TTC</span>
          <strong data-testid="total-ttc">{apercu ? dh(apercu.totaux.totalTtc) : vide ? dh(0) : "…"}</strong>
        </div>
        <div className="rangee-boutons">
          <button type="button" className="bouton bouton-contour" onClick={onAttente} disabled={!pret}>
            Mettre en attente
          </button>
          <button type="button" className="bouton bouton-plein" onClick={onEncaisser} disabled={!pret}>
            {apercu ? `Encaisser ${dh(apercu.totaux.totalTtc)}` : "Encaisser"}
          </button>
        </div>
      </div>
    </aside>
  );
}

export function libelleRemise(remise: Remise): string {
  return remise.type === "POURCENT" ? pourcent(remise.valeur) : dh(remise.valeur);
}
