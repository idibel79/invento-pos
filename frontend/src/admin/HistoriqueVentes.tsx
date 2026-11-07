import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useCaisses, useParametres } from "../caisse/requetes";
import { Dialogue } from "../ui/Dialogue";
import { dateHeure, decimal, dh, heure, pourcent } from "../ui/format";
import { useDetailVente, useHistoriqueVentes, useResumeVentes, useUtilisateurs } from "./requetes";

const MODES = { ESPECES: "Espèces", CARTE: "Carte" } as const;
const COLONNES = [
  "Heure",
  "Ticket",
  "Caisse",
  "Vendeur",
  "Articles",
  "Remise",
  "Montant remisé",
  "Validée par",
  "Paiement",
  "Montant reçu",
  "Rendu monnaie",
  "Total TTC",
];
const A_DROITE = ["Articles", "Montant remisé", "Montant reçu", "Rendu monnaie", "Total TTC"];

/** Jour courant (AAAA-MM-JJ) dans le fuseau de la boutique. */
function aujourdhui(fuseau: string | undefined): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: fuseau }).format(new Date());
}

function decaler(jour: string, jours: number): string {
  const date = new Date(`${jour}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + jours);
  return date.toISOString().slice(0, 10);
}

function remise(type: "POURCENT" | "MONTANT" | null | undefined, valeur: number | null | undefined): string {
  if (!type || valeur == null) return "";
  return type === "POURCENT" ? pourcent(valeur) : dh(valeur);
}

/** Historique des ventes validées d'un jour : filtres, totaux de la sélection, tableau paginé, détail d'une vente. */
export function HistoriqueVentes() {
  const fuseau = useParametres().data?.fuseauHoraire;
  const [jourChoisi, setJour] = useState<string>();
  const [caisseId, setCaisseId] = useState<number>();
  const [vendeurId, setVendeurId] = useState<number>();
  const [ouverte, setOuverte] = useState<number>();
  const jour = jourChoisi ?? aujourdhui(fuseau);
  const filtre = { jour, caisseId, vendeurId };
  const caisses = useCaisses().data ?? [];
  const vendeurs = useUtilisateurs().data ?? [];
  const ventes = useHistoriqueVentes(filtre);
  const resume = useResumeVentes(filtre);
  const liste = ventes.data?.pages.flatMap((p) => p.contenu) ?? [];

  return (
    <section className="admin-section admin-section-large">
      <div className="admin-tete">
        <div>
          <h1 className="admin-titre">Ventes</h1>
          <p className="texte-secondaire">
            Les ventes encaissées du jour choisi, de la plus récente à la plus ancienne. Cliquez sur une vente
            pour voir son détail.
          </p>
        </div>
      </div>

      <div className="barre-outils">
        <div className="filtre filtre-jour">
          <span>Jour :</span>
          <button
            type="button"
            className="bouton-jour"
            aria-label="Jour précédent"
            onClick={() => setJour(decaler(jour, -1))}
          >
            ‹
          </button>
          <input
            type="date"
            aria-label="Jour"
            value={jour}
            max={aujourdhui(fuseau)}
            onChange={(e) => setJour(e.target.value || undefined)}
          />
          <button
            type="button"
            className="bouton-jour"
            aria-label="Jour suivant"
            disabled={jour >= aujourdhui(fuseau)}
            onClick={() => setJour(decaler(jour, 1))}
          >
            ›
          </button>
        </div>
        <label className="filtre">
          <span>Caisse :</span>
          <select
            value={caisseId ?? ""}
            onChange={(e) => setCaisseId(e.target.value ? Number(e.target.value) : undefined)}
          >
            <option value="">Toutes</option>
            {caisses.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nom}
              </option>
            ))}
          </select>
        </label>
        <label className="filtre">
          <span>Vendeur :</span>
          <select
            value={vendeurId ?? ""}
            onChange={(e) => setVendeurId(e.target.value ? Number(e.target.value) : undefined)}
          >
            <option value="">Tous</option>
            {vendeurs.map((u) => (
              <option key={u.id} value={u.id}>
                {u.prenom} {u.nom}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="resume-remises">
        <Indicateur libelle="Ventes" valeur={resume.data ? String(resume.data.nombre) : "…"} />
        <Indicateur libelle="Chiffre d'affaires TTC" valeur={resume.data ? dh(resume.data.totalTtc) : "…"} />
        <Indicateur libelle="Remises accordées" valeur={resume.data ? dh(resume.data.remises) : "…"} />
        <Indicateur
          libelle="Espèces"
          valeur={resume.data ? dh(resume.data.especes) : "…"}
          detail={resume.data ? `Carte : ${dh(resume.data.carte)}` : undefined}
        />
      </div>

      {(ventes.isError || resume.isError) && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(ventes.error ?? resume.error)}
        </p>
      )}
      <div className="tableau-remises tableau-ventes" role="table" aria-label="Ventes">
        <div className="remise-ligne remise-entete" role="row">
          {COLONNES.map((titre) => (
            <span
              key={titre}
              role="columnheader"
              className={A_DROITE.includes(titre) ? "a-droite" : undefined}
            >
              {titre}
            </span>
          ))}
        </div>
        {ventes.isSuccess && liste.length === 0 && <p className="vide">Aucune vente ce jour-là.</p>}
        {liste.map((v) => (
          <LigneVente key={v.id} vente={v} fuseau={fuseau} onOuvrir={() => setOuverte(v.id)} />
        ))}
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
      {ouverte !== undefined && (
        <DetailVente id={ouverte} fuseau={fuseau} onFermer={() => setOuverte(undefined)} />
      )}
    </section>
  );
}

function Indicateur({ libelle, valeur, detail }: { libelle: string; valeur: string; detail?: string }) {
  return (
    <div className="indicateur">
      <span className="indicateur-libelle">{libelle}</span>
      <strong>{valeur}</strong>
      {detail && <span className="indicateur-libelle">{detail}</span>}
    </div>
  );
}

function LigneVente({
  vente: v,
  fuseau,
  onOuvrir,
}: {
  vente: Schemas["VenteHistorique"];
  fuseau: string | undefined;
  onOuvrir: () => void;
}) {
  return (
    <div className="remise-ligne vente-ligne" role="row" onClick={onOuvrir}>
      <span role="cell" className="remise-discret">
        {heure(v.le, fuseau)}
      </span>
      <span role="cell">
        <button
          type="button"
          className="lien-vente"
          onClick={(e) => {
            e.stopPropagation();
            onOuvrir();
          }}
        >
          {v.numero}
        </button>
      </span>
      <span role="cell">{v.caisse}</span>
      <span role="cell">{v.vendeur}</span>
      <span role="cell" className="a-droite">
        {v.articles}
      </span>
      <span role="cell" className="vente-portees">
        {v.remisesLignes > 0 && (
          <span className="pastille-portee ligne" title={`Remises sur les lignes : − ${dh(v.remisesLignes)}`}>
            Ligne
          </span>
        )}
        {v.remiseTicket > 0 && (
          <span className="pastille-portee ticket" title={`Remise sur le ticket : − ${dh(v.remiseTicket)}`}>
            Ticket
          </span>
        )}
        {v.remises === 0 && <span className="remise-discret">—</span>}
      </span>
      <span role="cell" className={v.remises > 0 ? "a-droite remise-montant" : "a-droite remise-discret"}>
        {v.remises > 0 ? `− ${dh(v.remises)}` : "—"}
      </span>
      <span role="cell" className={v.autorisePar ? undefined : "remise-discret"}>
        {v.autorisePar ?? "—"}
      </span>
      <span role="cell">{v.mode ? MODES[v.mode] : "—"}</span>
      <span role="cell" className={v.montantRecu != null ? "a-droite" : "a-droite remise-discret"}>
        {v.montantRecu != null ? dh(v.montantRecu) : "—"}
      </span>
      <span role="cell" className={v.rendu != null ? "a-droite" : "a-droite remise-discret"}>
        {v.rendu != null ? dh(v.rendu) : "—"}
      </span>
      <span role="cell" className="a-droite vente-total">
        {dh(v.totalTtc)}
      </span>
    </div>
  );
}

function DetailVente({
  id,
  fuseau,
  onFermer,
}: {
  id: number;
  fuseau: string | undefined;
  onFermer: () => void;
}) {
  const { data: v, isError, error } = useDetailVente(id);
  return (
    <Dialogue
      titre={v ? `Vente ${v.numero}` : "Vente"}
      sousTitre={v ? `${dateHeure(v.le, fuseau)} · ${v.caisse} · ${v.vendeur}` : undefined}
      onFermer={onFermer}
      largeur={720}
    >
      <div className="formulaire">
        {isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(error)}
          </p>
        )}
        {!v && !isError && <p className="texte-secondaire">Chargement…</p>}
        {v && (
          <>
            <div className="detail-vente" role="table" aria-label="Lignes de la vente">
              <div className="detail-vente-ligne detail-vente-entete" role="row">
                <span role="columnheader">Produit</span>
                <span role="columnheader" className="a-droite">
                  Qté
                </span>
                <span role="columnheader" className="a-droite">
                  Prix unit.
                </span>
                <span role="columnheader" className="a-droite">
                  Remise
                </span>
                <span role="columnheader" className="a-droite">
                  Montant
                </span>
              </div>
              {v.lignes.map((l, i) => (
                <div key={i} className="detail-vente-ligne" role="row">
                  <span role="cell" className="remise-produit">
                    {l.designation}
                  </span>
                  <span role="cell" className="a-droite">
                    {l.quantite}
                  </span>
                  <span role="cell" className="a-droite">
                    {decimal(l.prixUnitaire)}
                  </span>
                  <span
                    role="cell"
                    className={l.remiseMontant > 0 ? "a-droite remise-montant" : "a-droite remise-discret"}
                  >
                    {l.remiseMontant > 0
                      ? `${remise(l.remiseType, l.remiseValeur)} (− ${decimal(l.remiseMontant)})`
                      : "—"}
                  </span>
                  <span role="cell" className="a-droite">
                    {decimal(l.montantNet)}
                  </span>
                </div>
              ))}
            </div>

            <div className="recap-totaux detail-vente-totaux">
              <span>
                <span>Sous-total</span>
                <span>{dh(v.sousTotal)}</span>
              </span>
              {v.remisesLignes > 0 && (
                <span>
                  <span>Remises sur les lignes</span>
                  <span>− {dh(v.remisesLignes)}</span>
                </span>
              )}
              {v.remiseTicketMontant != null && v.remiseTicketMontant > 0 && (
                <span>
                  <span>Remise sur le ticket ({remise(v.remiseTicketType, v.remiseTicketValeur)})</span>
                  <span>− {dh(v.remiseTicketMontant)}</span>
                </span>
              )}
              <span>
                <span>Total HT</span>
                <span>{dh(v.totalHt)}</span>
              </span>
              <span>
                <span>TVA</span>
                <span>{dh(v.totalTva)}</span>
              </span>
            </div>
            <div className="total-a-payer">
              <span>Total TTC</span>
              <strong>{dh(v.totalTtc)}</strong>
            </div>

            <dl className="detail-vente-infos">
              <dt>Paiement</dt>
              <dd>
                {v.mode ? MODES[v.mode] : "Aucun (vente à 0 DH)"}
                {v.mode === "ESPECES" && v.montantRecu != null && (
                  <>
                    {" "}
                    · reçu {dh(v.montantRecu)} · rendu {dh(v.rendu ?? 0)}
                  </>
                )}
              </dd>
              {v.motifRemise && (
                <>
                  <dt>Motif de remise</dt>
                  <dd>{v.motifRemise}</dd>
                </>
              )}
              {v.autorisePar && (
                <>
                  <dt>Remise autorisée par</dt>
                  <dd>{v.autorisePar}</dd>
                </>
              )}
            </dl>
          </>
        )}
        <div className="rangee-boutons">
          <button type="button" className="bouton bouton-contour" onClick={onFermer}>
            Fermer
          </button>
        </div>
      </div>
    </Dialogue>
  );
}
