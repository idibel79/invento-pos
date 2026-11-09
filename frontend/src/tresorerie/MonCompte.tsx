import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useParametres } from "../caisse/requetes";
import { decimal, heure } from "../ui/format";
import { AValiderDialogue, VerserDialogue } from "./ComptePopups";
import { decalerMois, grilleMois, libelleJour, libelleMois } from "./calendrier";
import { useCalendrier, useJournee, useMonCompte } from "./requetes";
import { StatutVersement } from "./StatutVersement";

type Ligne = Schemas["LigneJourneeDto"];

const JOURS_SEMAINE = ["L", "M", "M", "J", "V", "S", "D"];

/** Jour courant (AAAA-MM-JJ) dans le fuseau de la boutique. */
function aujourdhui(fuseau: string | undefined): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: fuseau }).format(new Date());
}

function signe(montant: number): string {
  if (montant === 0) return decimal(0);
  return `${montant > 0 ? "+" : "−"} ${decimal(Math.abs(montant))}`;
}

/**
 * Rubrique Mon compte de l'administration (maquette B) : solde dû et mini-calendrier à gauche, journée
 * choisie à droite avec ses totaux et la chronologie de toutes les opérations de l'utilisateur.
 */
export function MonCompte() {
  const fuseau = useParametres().data?.fuseauHoraire;
  const jourCourant = aujourdhui(fuseau);
  const [jour, setJour] = useState(jourCourant);
  const [mois, setMois] = useState(jourCourant.slice(0, 7));
  const [popup, setPopup] = useState<"verser" | "valider" | null>(null);
  const compte = useMonCompte();
  const calendrier = useCalendrier(mois);
  const journee = useJournee(jour);
  const actifs = new Set(calendrier.data?.jours ?? []);
  const aValider = compte.data?.aValider.length ?? 0;

  return (
    <>
      <div className="mon-compte-corps">
        <aside className="mon-compte-gauche">
          <section className="carte-blanche">
            <span className="texte-gris">Solde dû à la caisse principale</span>
            <strong className="tresorerie-solde-montant">
              {compte.data ? decimal(compte.data.solde) : "…"} <span>DH</span>
            </strong>
            <div className="mon-compte-actions">
              <button type="button" className="bouton-compact plein" onClick={() => setPopup("verser")}>
                Verser
              </button>
              <button type="button" className="bouton-compact" onClick={() => setPopup("valider")}>
                À valider ({aValider})
              </button>
            </div>
          </section>

          <section className="carte-blanche" aria-label="Calendrier">
            <div className="calendrier-tete">
              <button
                type="button"
                className="calendrier-fleche"
                aria-label="Mois précédent"
                onClick={() => setMois(decalerMois(mois, -1))}
              >
                ‹
              </button>
              <strong>{libelleMois(mois)}</strong>
              <button
                type="button"
                className="calendrier-fleche"
                aria-label="Mois suivant"
                disabled={mois >= jourCourant.slice(0, 7)}
                onClick={() => setMois(decalerMois(mois, 1))}
              >
                ›
              </button>
            </div>
            <div className="calendrier-grille calendrier-semaine" aria-hidden="true">
              {JOURS_SEMAINE.map((j, i) => (
                <span key={i}>{j}</span>
              ))}
            </div>
            <div className="calendrier-grille">
              {grilleMois(mois).map((c) => (
                <button
                  key={c.date}
                  type="button"
                  className={`calendrier-jour${c.dansLeMois ? "" : " hors-mois"}${
                    c.date === jourCourant ? " aujourdhui" : ""
                  }`}
                  aria-pressed={c.date === jour}
                  aria-label={`${libelleJour(c.date)}${actifs.has(c.date) ? ", avec opérations" : ""}`}
                  disabled={c.date > jourCourant}
                  onClick={() => {
                    setJour(c.date);
                    if (!c.dansLeMois) setMois(c.date.slice(0, 7));
                  }}
                >
                  <span>{c.numero}</span>
                  <span className={actifs.has(c.date) ? "calendrier-point" : "calendrier-point absent"} />
                </button>
              ))}
            </div>
            <span className="texte-gris petit">Un point = au moins une opération ce jour-là.</span>
          </section>
        </aside>

        <main className="mon-compte-droite">
          <div className="mon-compte-titre">
            <h1>{libelleJour(jour)}</h1>
            {journee.data && journee.data.sessions.length > 0 && (
              <span className="texte-gris">
                {journee.data.sessions
                  .map(
                    (s) =>
                      `${s.caisse} · ${heure(s.ouverture, fuseau)} – ${
                        s.fermeture ? heure(s.fermeture, fuseau) : "en cours"
                      }`,
                  )
                  .join(", ")}
              </span>
            )}
          </div>

          {journee.isPending && <p className="chargement">Chargement…</p>}
          {journee.isError && (
            <p className="alerte alerte-danger" role="alert">
              {messageErreur(journee.error)}
            </p>
          )}
          {journee.data && (
            <>
              <div className="mon-compte-tuiles">
                <Tuile libelle="Solde début" valeur={decimal(journee.data.soldeDebut)} />
                <Tuile libelle="Reçu" valeur={signe(journee.data.recu)} classe="positif" />
                <Tuile libelle="Ventes" valeur={signe(journee.data.ventes)} classe="positif" />
                <Tuile libelle="Versé" valeur={signe(-journee.data.verse)} classe="negatif" />
                <Tuile libelle="Solde fin" valeur={decimal(journee.data.soldeFin)} />
              </div>
              <section className="carte-blanche chronologie" aria-label="Opérations de la journée">
                {journee.data.lignes.length === 0 ? (
                  <p className="vide">Aucune opération ce jour-là.</p>
                ) : (
                  <ol className="chronologie-liste">
                    {journee.data.lignes.map((l, i) => (
                      <LigneChronologie key={i} ligne={l} fuseau={fuseau} />
                    ))}
                  </ol>
                )}
              </section>
            </>
          )}
        </main>
      </div>
      {popup === "verser" && <VerserDialogue onFermer={() => setPopup(null)} />}
      {popup === "valider" && <AValiderDialogue onFermer={() => setPopup(null)} />}
    </>
  );
}

function Tuile({ libelle, valeur, classe }: { libelle: string; valeur: string; classe?: string }) {
  return (
    <div className="carte-blanche mon-compte-tuile">
      <span className="texte-gris">{libelle}</span>
      <strong className={classe && valeur !== decimal(0) ? `montant-signe ${classe}` : undefined}>
        {valeur}
      </strong>
    </div>
  );
}

/** Libellé, détail et couleur de pastille d'un événement de la journée. */
function decrire(l: Ligne, fuseau: string | undefined): { libelle: string; detail: string; puce: string } {
  const a = (iso: string | null | undefined) => (iso ? ` à ${heure(iso, fuseau)}` : "");
  switch (l.type) {
    case "OUVERTURE":
      return { libelle: `Ouverture ${l.caisse ?? ""}`, detail: "Fond pris sur mon compte", puce: "gris" };
    case "VENTES":
      return {
        libelle: `Ventes de la session${l.caisse ? ` · ${l.caisse}` : ""}`,
        detail: `${l.tickets ?? 0} ticket${(l.tickets ?? 0) > 1 ? "s" : ""} · espèces ${decimal(l.especes ?? 0)} · carte ${decimal(l.carte ?? 0)}`,
        puce: "vert",
      };
    case "CLOTURE": {
      const libelle = `${l.clotureForcee ? "Clôture d'office" : "Clôture"} ${l.caisse ?? ""}`;
      if (l.comptageEnAttente) {
        return { libelle, detail: "Caisse désactivée · comptage du tiroir en attente", puce: "rouge" };
      }
      const ecart = l.ecart ?? 0;
      const texteEcart =
        ecart < 0
          ? `écart ${signe(ecart)} à compléter de ma poche`
          : ecart > 0
            ? `écart ${signe(ecart)} ajouté à mon solde`
            : "sans écart";
      return {
        libelle,
        detail: `${l.clotureForcee ? "Compté par l'admin" : "Compté"} ${decimal(l.montantCompte ?? 0)} · ${texteEcart}`,
        puce: "rouge",
      };
    }
    case "VERSEMENT": {
      if (l.versLaPrincipale) {
        const nature = l.cloture
          ? "Versement de clôture"
          : l.caisse
            ? "Versement en cours de session"
            : "Versement";
        const suite =
          l.statut === "VALIDE"
            ? l.autoValide
              ? "auto-validé"
              : `validé par ${l.traitePar ?? "l'admin"}${a(l.traiteLe)}`
            : l.statut === "REFUSE"
              ? `refusé${l.motif ? ` : ${l.motif}` : ""}`
              : l.statut === "ANNULE"
                ? `annulé${a(l.traiteLe)}`
                : "en attente de validation par l'admin";
        return { libelle: "Versé à la caisse principale", detail: `${nature} · ${suite}`, puce: "bleu" };
      }
      const suite =
        l.statut === "VALIDE"
          ? `validé${a(l.traiteLe)}`
          : l.statut === "REFUSE"
            ? `refusé${l.motif ? ` : ${l.motif}` : ""}`
            : l.statut === "ANNULE"
              ? "annulé par l'admin"
              : "à valider dans Mon compte";
      const motif = l.statut !== "REFUSE" && l.motif ? ` · ${l.motif}` : "";
      return {
        libelle: "Reçu de la caisse principale",
        detail: `Envoyé par ${l.creePar ?? "l'admin"}${motif} · ${suite}`,
        puce: "bleu",
      };
    }
    case "AJUSTEMENT":
      return {
        libelle: "Ajustement (passage en perte)",
        detail: `${l.motif ?? ""}${l.creePar ? ` · par ${l.creePar}` : ""}`,
        puce: "rouge",
      };
  }
}

function LigneChronologie({ ligne: l, fuseau }: { ligne: Ligne; fuseau: string | undefined }) {
  const { libelle, detail, puce } = decrire(l, fuseau);
  // Un versement refusé ou annulé est revenu : il ne compte pas dans le solde.
  const sansEffet = l.statut === "REFUSE" || l.statut === "ANNULE";
  const neutre = l.type === "OUVERTURE" || l.montant === 0 || sansEffet;
  return (
    <li className="chronologie-ligne">
      <span className="texte-gris">{heure(l.le, fuseau)}</span>
      <span className={`chronologie-puce ${puce}`} aria-hidden="true" />
      <span className="chronologie-texte">
        <span className="chronologie-libelle">{libelle}</span>
        <span className="texte-gris petit">{detail}</span>
      </span>
      <span>
        {l.type === "VERSEMENT" && (
          <StatutVersement
            statut={l.statut}
            autoValide={l.autoValide}
            versLaPrincipale={l.versLaPrincipale}
          />
        )}
      </span>
      <strong
        className={`chronologie-montant${neutre ? (sansEffet ? " barre" : "") : ` montant-signe ${l.montant < 0 ? "negatif" : "positif"}`}`}
      >
        {l.type === "OUVERTURE" ? decimal(l.montant) : signe(l.montant)}
      </strong>
    </li>
  );
}
