import { useMutation } from "@tanstack/react-query";
import { type ReactNode, useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { api, type Schemas } from "../api/client";
import { appel } from "../api/erreurs";
import { useParametres } from "../caisse/requetes";
import { decimal, dh, pourcent } from "../ui/format";

type Facture = Schemas["FactureDto"];
type Boutique = Schemas["Parametres"];

function modePaiement(vente: Schemas["VenteDto"]): string {
  if (vente.paiements.length > 1) return "espèces + carte bancaire";
  switch (vente.paiement?.mode) {
    case "CREDIT":
      return "à crédit";
    case "CARTE":
      return "carte bancaire";
    case "ESPECES":
      return "espèces";
    default:
      return "—";
  }
}

/** Identifiants légaux renseignés, sur une ligne : « ICE 0012… · IF 4012… ». */
function identifiants(valeurs: [string, string | null | undefined][]): string {
  return valeurs
    .filter(([, v]) => v?.trim())
    .map(([libelle, v]) => `${libelle} ${v?.trim()}`)
    .join(" · ");
}

function date(iso: string, fuseau: string | undefined): string {
  return new Intl.DateTimeFormat("fr-FR", { dateStyle: "short", timeZone: fuseau }).format(new Date(iso));
}

/**
 * Facture A4 d'une vente avec client : en-tête et identifiants légaux de la boutique (Paramètres ›
 * Boutique), client figé à l'émission, lignes TTC, ventilation de la TVA et total en lettres. Tous les
 * montants viennent du serveur.
 */
export function FactureA4({ facture, boutique }: { facture: Facture; boutique: Boutique | undefined }) {
  const { vente, client } = facture;
  const t = vente.totaux;
  const fuseau = boutique?.fuseauHoraire;
  const legauxBoutique = identifiants([
    ["ICE", boutique?.boutiqueIce],
    ["IF", boutique?.boutiqueIf],
    ["RC", boutique?.boutiqueRc],
    ["Patente", boutique?.boutiquePatente],
  ]);
  const legauxClient = identifiants([
    ["ICE", client.ice],
    ["IF", client.identifiantFiscal],
    ["RC", client.rc],
  ]);

  return (
    <article className="facture-a4" aria-label={`Facture ${facture.numero}`}>
      <header className="facture-entete">
        <div className="facture-boutique">
          {boutique?.boutiqueNom && <strong className="facture-boutique-nom">{boutique.boutiqueNom}</strong>}
          {boutique?.boutiqueAdresse && <span>{boutique.boutiqueAdresse}</span>}
          {(boutique?.boutiqueTelephone || boutique?.boutiqueEmail) && (
            <span>
              {[boutique.boutiqueTelephone && `Tél. : ${boutique.boutiqueTelephone}`, boutique.boutiqueEmail]
                .filter(Boolean)
                .join(" · ")}
            </span>
          )}
          {legauxBoutique && <span className="facture-legaux">{legauxBoutique}</span>}
        </div>
        <div className="facture-numero">
          <h1>FACTURE</h1>
          <strong>N° {facture.numero}</strong>
          <span>Date : {date(facture.emiseLe, fuseau)}</span>
          <span className="facture-legaux">Ticket {vente.numero}</span>
        </div>
      </header>

      <section className="facture-client" aria-label="Client">
        <span className="facture-etiquette">CLIENT</span>
        <strong>{client.nom}</strong>
        {client.adresse && <span>{client.adresse}</span>}
        {legauxClient && <span>{legauxClient}</span>}
        {client.contact && <span className="facture-legaux">À l'attention de {client.contact}</span>}
      </section>

      <table className="facture-lignes">
        <thead>
          <tr>
            <th scope="col">Désignation</th>
            <th scope="col">Qté</th>
            <th scope="col">PU TTC</th>
            <th scope="col">Remise</th>
            <th scope="col">TVA</th>
            <th scope="col">Montant TTC</th>
          </tr>
        </thead>
        <tbody>
          {vente.lignes.map((l) => (
            <tr key={l.produitId}>
              <td>{l.designation}</td>
              <td>{l.quantite}</td>
              <td>{decimal(l.prixUnitaire)}</td>
              <td>{l.remiseMontant > 0 ? `−${decimal(l.remiseMontant)}` : "—"}</td>
              <td>{pourcent(l.tauxTva)}</td>
              <td>{decimal(l.montantNet)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="facture-bas">
        <table className="facture-tva">
          <thead>
            <tr>
              <th scope="col">Taux TVA</th>
              <th scope="col">Base HT</th>
              <th scope="col">TVA</th>
            </tr>
          </thead>
          <tbody>
            {t.tva.map((v) => (
              <tr key={v.taux}>
                <td>{pourcent(v.taux)}</td>
                <td>{decimal(v.totalHt)}</td>
                <td>{decimal(v.totalTva)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <dl className="facture-totaux">
          {t.remiseTicket > 0 && (
            <div>
              <dt>Remise sur le total</dt>
              <dd>−{dh(t.remiseTicket)}</dd>
            </div>
          )}
          <div>
            <dt>Total HT</dt>
            <dd>{dh(t.totalHt)}</dd>
          </div>
          <div>
            <dt>Total TVA</dt>
            <dd>{dh(t.totalTva)}</dd>
          </div>
          <div className="facture-ttc">
            <dt>Total TTC</dt>
            <dd>{dh(t.totalTtc)}</dd>
          </div>
        </dl>
      </div>

      <div className="facture-mentions">
        <p>
          Arrêtée la présente facture à la somme de : <strong>{facture.montantEnLettres}</strong>.
        </p>
        <p>
          Mode de paiement : <strong>{modePaiement(vente)}</strong>
        </p>
      </div>

      {(boutique?.boutiqueNom || legauxBoutique) && (
        <footer className="facture-pied">
          {[boutique?.boutiqueNom, boutique?.boutiqueAdresse, legauxBoutique].filter(Boolean).join(" · ")}
        </footer>
      )}
    </article>
  );
}

/**
 * Impression d'une facture A4 : le serveur l'émet à la première demande (numéro attribué) et renvoie la
 * même ensuite. `rendu` est à placer dans l'écran ; il n'est visible qu'à l'impression.
 */
export function useImpressionFacture(): {
  imprimer: (venteId: number) => void;
  enCours: boolean;
  erreur: Error | null;
  rendu: ReactNode;
} {
  const boutique = useParametres().data;
  const [facture, setFacture] = useState<Facture | null>(null);
  const facturer = useMutation({
    mutationFn: (venteId: number) =>
      appel(api.POST("/api/v1/ventes/{id}/facture", { params: { path: { id: venteId } } })),
    onSuccess: setFacture,
  });

  // Une fois la facture dans la page : seule elle s'imprime (pas le ticket ni le relevé ouverts).
  useEffect(() => {
    if (!facture) return;
    document.body.classList.add("impression-facture");
    const fin = () => setFacture(null);
    window.addEventListener("afterprint", fin);
    window.print();
    return () => {
      window.removeEventListener("afterprint", fin);
      document.body.classList.remove("impression-facture");
    };
  }, [facture]);

  return {
    imprimer: (venteId) => facturer.mutate(venteId),
    enCours: facturer.isPending,
    erreur: facturer.error,
    rendu:
      facture &&
      createPortal(
        <div className="zone-impression zone-facture">
          <FactureA4 facture={facture} boutique={boutique} />
        </div>,
        document.body,
      ),
  };
}
