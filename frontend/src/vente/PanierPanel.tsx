import type { Dispatch } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { dh, pourcent } from "../ui/format";
import { IconeCorbeille } from "../ui/icones";
import { nombreArticles, type ActionPanier, type EtatPanier, type Remise } from "./panier";

interface Props {
  panier: EtatPanier;
  dispatch: Dispatch<ActionPanier>;
  calcul: { data: Schemas["PanierDto"] | undefined; error: Error | null; isFetching: boolean };
  onRemiseLigne: (produitId: number) => void;
  onRemiseTicket: () => void;
  onAttente: () => void;
  onEncaisser: () => void;
  onVider: () => void;
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
  onVider,
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
  // Qui a autorisé les remises de ce ticket (une autorisation par article remisé, ou le ticket).
  const autorisations = [
    ...new Set(
      [panier.autorisePar, ...panier.lignes.map((l) => l.autorisePar)].filter((n): n is string => n !== null),
    ),
  ];
  // Une seule remise par vente (règle vérifiée par le serveur) : remises sur les lignes ou remise ticket.
  const remiseSurLignes = panier.lignes.some((l) => l.remise !== null);
  const remiseTicket = panier.remiseTicket !== null;

  return (
    <aside className="panier" aria-label="Vente en cours">
      <div className="panier-tete">
        <div>
          <div className="panier-titre">
            {panier.numeroEnAttente ? `Vente reprise ${panier.numeroEnAttente}` : "Vente en cours"}
          </div>
          <div className="panier-sous-titre">
            {articles} article{articles > 1 ? "s" : ""}
            {autorisations.length > 0 ? ` · remise autorisée par ${autorisations.join(", ")}` : ""}
          </div>
        </div>
        <button
          type="button"
          className="bouton-petit danger"
          onClick={onVider}
          disabled={vide && panier.venteEnAttenteId === null}
        >
          Vider
        </button>
      </div>

      <div className="panier-lignes">
        {vide && <p className="vide">Scannez ou touchez un produit pour l'ajouter.</p>}
        {panier.lignes.map((ligne) => {
          const calculee = lignesCalculees.get(ligne.produitId);
          return (
            <div className="ligne" key={ligne.produitId}>
              <div className="ligne-haut">
                <button
                  type="button"
                  className="bouton-supprimer"
                  aria-label={`Retirer ${ligne.designation} du ticket`}
                  title="Retirer l'article"
                  onClick={() => dispatch({ type: "retirer", produitId: ligne.produitId })}
                >
                  <IconeCorbeille />
                </button>
                <span className="ligne-nom" title={ligne.designation}>
                  {ligne.designation}
                </span>
              </div>
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
                <span className="ligne-prix">{dh(calculee?.prixUnitaire ?? ligne.prixUnitaire)} l'unité</span>
                <button
                  type="button"
                  className={`bouton-remise${ligne.remise ? " active" : ""}`}
                  aria-label={`Remise sur ${ligne.designation}`}
                  onClick={() => onRemiseLigne(ligne.produitId)}
                  disabled={remiseTicket}
                  title={remiseTicket ? "Retirez d'abord la remise sur le ticket" : undefined}
                >
                  {ligne.remise ? `Remise − ${libelleRemise(ligne.remise)}` : "Remise"}
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
        <div className="bloc-totaux">
          <div className="total-ligne">
            <span>Prix sans remise</span>
            <span>{apercu ? dh(apercu.totaux.sousTotal) : "–"}</span>
          </div>
          <div className="total-ligne">
            <span>Remises</span>
            <span>
              {apercu ? `${apercu.totaux.totalRemises > 0 ? "−" : ""}${dh(apercu.totaux.totalRemises)}` : "–"}
            </span>
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
            <strong data-testid="total-ttc">
              {apercu ? dh(apercu.totaux.totalTtc) : vide ? dh(0) : "…"}
            </strong>
          </div>
        </div>
        <div className="rangee-boutons">
          <button
            type="button"
            className="bouton bouton-contour"
            onClick={onRemiseTicket}
            disabled={vide || remiseSurLignes}
            title={remiseSurLignes ? "Retirez d'abord les remises sur les lignes" : undefined}
          >
            {panier.remiseTicket ? `Remise ticket ${libelleRemise(panier.remiseTicket)}` : "Remise ticket"}
          </button>
          <button type="button" className="bouton bouton-contour" onClick={onAttente} disabled={!pret}>
            Mettre en attente
          </button>
        </div>
        <button
          type="button"
          className="bouton bouton-plein bouton-encaisser"
          onClick={onEncaisser}
          disabled={!pret}
        >
          <span>Encaisser</span>
          {apercu && <span>{dh(apercu.totaux.totalTtc)} ›</span>}
        </button>
      </div>
    </aside>
  );
}

export function libelleRemise(remise: Remise): string {
  return remise.type === "POURCENT" ? pourcent(remise.valeur) : dh(remise.valeur);
}
