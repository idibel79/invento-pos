import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { dh } from "../ui/format";
import { useMaintenant } from "../ui/hooks";
import { useAbandonnerVente, useReprendreVente, useVentesEnAttente } from "./requetes";

interface Props {
  panierVide: boolean;
  /** Vente déjà reprise dans le panier : elle n'apparaît plus dans la liste. */
  venteRepriseId: number | null;
  onReprise: (vente: Schemas["VenteDto"]) => void;
  onFermer: () => void;
}

/** Durée de la réservation du stock d'une vente en attente (règle serveur : 5 minutes). */
const DUREE_ATTENTE_MS = 5 * 60 * 1000;
/** En dessous d'une minute, le temps restant passe en rouge. */
const SEUIL_URGENT_MS = 60 * 1000;

function msRestantes(expireLe: string | null, maintenant: number): number {
  return expireLe ? Math.max(0, new Date(expireLe).getTime() - maintenant) : 0;
}

function minutesSecondes(ms: number): string {
  const secondes = Math.round(ms / 1000);
  return `${Math.floor(secondes / 60)}:${String(secondes % 60).padStart(2, "0")}`;
}

/** Ventes en attente de la caisse en tableau, la plus proche de l'annulation en premier. */
export function VentesEnAttenteDialogue({ panierVide, venteRepriseId, onReprise, onFermer }: Props) {
  const ventes = useVentesEnAttente();
  const reprendre = useReprendreVente();
  const abandonner = useAbandonnerVente();
  const maintenant = useMaintenant();
  const erreur = reprendre.error ?? abandonner.error;
  const liste = (ventes.data ?? [])
    .filter((v) => v.id !== venteRepriseId)
    .sort((a, b) => msRestantes(a.attenteExpireLe, maintenant) - msRestantes(b.attenteExpireLe, maintenant));

  return (
    <Dialogue
      titre="Ventes en attente"
      sousTitre={`${liste.length} vente${liste.length > 1 ? "s" : ""} · annulation automatique après 5 min`}
      onFermer={onFermer}
      largeur={880}
    >
      {!panierVide && (
        <p className="alerte alerte-attention">
          Encaissez, videz ou mettez en attente la vente en cours avant d'en reprendre une autre.
        </p>
      )}
      {ventes.isSuccess && liste.length === 0 && <p className="vide">Aucune vente en attente.</p>}
      {liste.length > 0 && (
        <div className="tableau-attente">
          <div className="attente-ligne attente-entete" aria-hidden="true">
            <span>Ticket</span>
            <span>Articles · note</span>
            <span className="a-droite">Total</span>
            <span>Expire dans</span>
            <span />
          </div>
          {liste.map((v) => {
            const ms = msRestantes(v.attenteExpireLe, maintenant);
            const urgent = ms < SEUIL_URGENT_MS;
            const [premiere, ...autres] = v.lignes;
            return (
              <div className="attente-ligne" key={v.id}>
                <span className="attente-numero">{v.numero}</span>
                <span className="attente-articles">
                  <span title={v.lignes.map((l) => `${l.quantite} × ${l.designation}`).join("\n")}>
                    {premiere && `${premiere.quantite} × ${premiere.designation}`}
                    {autres.length > 0 && (
                      <span className="attente-autres">
                        {" "}
                        + {autres.length} autre{autres.length > 1 ? "s" : ""}
                      </span>
                    )}
                  </span>
                  <span className="attente-note">{v.noteAttente || "—"}</span>
                </span>
                <span className="a-droite attente-total">{dh(v.totaux.totalTtc)}</span>
                <span className={`attente-temps${urgent ? " urgent" : ""}`}>
                  <span>{minutesSecondes(ms)}</span>
                  <span className="attente-barre" aria-hidden="true">
                    <span style={{ width: `${Math.min(100, (ms / DUREE_ATTENTE_MS) * 100)}%` }} />
                  </span>
                </span>
                <span className="attente-actions">
                  <button
                    type="button"
                    className="bouton-lien danger"
                    aria-label={`Abandonner la vente ${v.numero}`}
                    disabled={abandonner.isPending}
                    onClick={() => abandonner.mutate(v.id)}
                  >
                    Abandonner
                  </button>
                  <button
                    type="button"
                    className="bouton bouton-plein bouton-compact"
                    disabled={!panierVide || reprendre.isPending}
                    onClick={() => reprendre.mutate(v.id, { onSuccess: onReprise })}
                  >
                    Reprendre
                  </button>
                </span>
              </div>
            );
          })}
        </div>
      )}
      {erreur && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(erreur)}
        </p>
      )}
      <div className="rangee-boutons">
        <button type="button" className="bouton bouton-contour" onClick={onFermer}>
          Fermer
        </button>
      </div>
    </Dialogue>
  );
}
