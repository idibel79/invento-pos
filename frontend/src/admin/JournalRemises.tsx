import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useUtilisateur } from "../auth/AuthContext";
import { useCaisses, useParametres } from "../caisse/requetes";
import { dh, pourcent } from "../ui/format";
import { FiltreClient, useRechercheClient } from "./FiltreClient";
import { useJournalRemises, useResumeRemises, useUtilisateurs, type FiltreRemises } from "./requetes";

const PERIODES = { "7": "7 derniers jours", "30": "30 derniers jours", tout: "Tout l'historique" } as const;
const PORTEES = { toutes: "Toutes", LIGNE: "Sur une ligne", TICKET: "Sur le ticket" } as const;

/** Journal des remises (design A) : filtres, totaux de la sélection, tableau paginé des remises. */
export function JournalRemises() {
  const [periode, setPeriode] = useState<keyof typeof PERIODES>("7");
  const [portee, setPortee] = useState<keyof typeof PORTEES>("toutes");
  const [caisseId, setCaisseId] = useState<number>();
  const [vendeurId, setVendeurId] = useState<number>();
  const [saisieClient, setSaisieClient, client] = useRechercheClient();
  const filtre: FiltreRemises = {
    client,
    jours: periode === "tout" ? undefined : Number(periode),
    portee: portee === "toutes" ? undefined : portee,
    caisseId,
    vendeurId,
  };
  const caisses = useCaisses().data ?? [];
  // Hors admin, le serveur ne renvoie que les ventes de l'utilisateur : pas de filtre par vendeur.
  const admin = useUtilisateur().role === "ADMIN";
  const vendeurs = useUtilisateurs(admin).data ?? [];
  const remises = useJournalRemises(filtre);
  const resume = useResumeRemises(filtre);
  const fuseau = useParametres().data?.fuseauHoraire;
  const liste = remises.data?.pages.flatMap((p) => p.contenu) ?? [];

  return (
    <section className="admin-section admin-section-large">
      <div className="admin-tete">
        <div>
          <h1 className="admin-titre">Journal des remises</h1>
          <p className="texte-secondaire">
            {admin ? "Toutes les remises accordées" : "Les remises accordées sur vos ventes"} validées, de la
            plus récente à la plus ancienne.
          </p>
        </div>
      </div>

      <div className="barre-outils">
        <label className="filtre">
          <span>Période :</span>
          <select value={periode} onChange={(e) => setPeriode(e.target.value as keyof typeof PERIODES)}>
            {Object.entries(PERIODES).map(([cle, libelle]) => (
              <option key={cle} value={cle}>
                {libelle}
              </option>
            ))}
          </select>
        </label>
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
        {admin && (
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
        )}
        <label className="filtre">
          <span>Portée :</span>
          <select value={portee} onChange={(e) => setPortee(e.target.value as keyof typeof PORTEES)}>
            {Object.entries(PORTEES).map(([cle, libelle]) => (
              <option key={cle} value={cle}>
                {libelle}
              </option>
            ))}
          </select>
        </label>
        <FiltreClient valeur={saisieClient} onChange={setSaisieClient} />
      </div>

      <div className="resume-remises">
        <Indicateur libelle="Remises" valeur={resume.data ? String(resume.data.nombre) : "…"} />
        <Indicateur
          libelle="Montant total remisé"
          valeur={resume.data ? dh(resume.data.montantTotal) : "…"}
        />
        <Indicateur
          libelle="Avec autorisation"
          valeur={resume.data ? String(resume.data.autorisees) : "…"}
          detail="responsable ou admin"
        />
      </div>

      {(remises.isError || resume.isError) && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(remises.error ?? resume.error)}
        </p>
      )}
      <div className="tableau-remises" role="table" aria-label="Remises">
        <div className="remise-ligne remise-entete" role="row">
          {[
            "Date",
            "Vente",
            "Caisse",
            "Vendeur",
            "Client",
            "Autorisée par",
            "Portée",
            "Produit",
            "Remise",
            "Montant",
            "Motif",
          ].map((titre) => (
            <span key={titre} role="columnheader" className={titre === "Montant" ? "a-droite" : undefined}>
              {titre}
            </span>
          ))}
        </div>
        {remises.isSuccess && liste.length === 0 && <p className="vide">Aucune remise sur cette période.</p>}
        {liste.map((r) => (
          <LigneRemise key={`${r.venteNumero}-${r.portee}-${r.produit ?? ""}`} remise={r} fuseau={fuseau} />
        ))}
        {remises.hasNextPage && (
          <button
            type="button"
            className="tableau-suite"
            onClick={() => void remises.fetchNextPage()}
            disabled={remises.isFetchingNextPage}
          >
            Afficher plus de remises
          </button>
        )}
      </div>
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

function LigneRemise({
  remise: r,
  fuseau,
}: {
  remise: Schemas["RemiseJournal"];
  fuseau: string | undefined;
}) {
  const date = new Intl.DateTimeFormat("fr-FR", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    timeZone: fuseau,
  }).format(new Date(r.le));
  const remise = r.valeur === undefined ? "—" : r.type === "POURCENT" ? pourcent(r.valeur) : dh(r.valeur);
  return (
    <div className="remise-ligne" role="row">
      <span role="cell" className="remise-discret">
        {date}
      </span>
      <span role="cell" className="remise-vente">
        {r.venteNumero}
      </span>
      <span role="cell">{r.caisse}</span>
      <span role="cell">{r.vendeur}</span>
      <span role="cell" className={r.client ? undefined : "remise-discret"}>
        {r.client ?? "—"}
      </span>
      <span role="cell" className={r.autorisePar ? undefined : "remise-discret"}>
        {r.autorisePar ?? "—"}
      </span>
      <span role="cell">
        <span className={`pastille-portee ${r.portee === "LIGNE" ? "ligne" : "ticket"}`}>
          {r.portee === "LIGNE" ? "Ligne" : "Ticket"}
        </span>
      </span>
      <span role="cell" className="remise-produit" title={r.produit ?? undefined}>
        {r.produit ? `${r.produit} × ${r.quantite ?? 1}` : "—"}
      </span>
      <span role="cell">{remise}</span>
      <span role="cell" className="a-droite remise-montant">
        − {dh(r.montant)}
      </span>
      <span role="cell" className={r.motif ? "remise-motif" : "remise-discret"} title={r.motif ?? undefined}>
        {r.motif ?? "—"}
      </span>
    </div>
  );
}
