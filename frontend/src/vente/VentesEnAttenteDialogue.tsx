import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { dh } from "../ui/format";
import { useMaintenant } from "../ui/hooks";
import { IconeHorloge } from "../ui/icones";
import { useAbandonnerVente, useReprendreVente, useVentesEnAttente } from "./requetes";

interface Props {
  panierVide: boolean;
  onReprise: (vente: Schemas["VenteDto"]) => void;
  onFermer: () => void;
}

function restant(expireLe: string | null, maintenant: number): string {
  if (!expireLe) return "";
  const secondes = Math.max(0, Math.round((new Date(expireLe).getTime() - maintenant) / 1000));
  return `${Math.floor(secondes / 60)}:${String(secondes % 60).padStart(2, "0")}`;
}

export function VentesEnAttenteDialogue({ panierVide, onReprise, onFermer }: Props) {
  const ventes = useVentesEnAttente();
  const reprendre = useReprendreVente();
  const abandonner = useAbandonnerVente();
  const maintenant = useMaintenant();
  const erreur = reprendre.error ?? abandonner.error;

  return (
    <Dialogue titre="Ventes en attente" icone={<IconeHorloge />} onFermer={onFermer} largeur={560}>
      {!panierVide && (
        <p className="alerte alerte-attention">
          Encaissez, videz ou mettez en attente la vente en cours avant d'en reprendre une autre.
        </p>
      )}
      {ventes.isSuccess && ventes.data.length === 0 && <p className="vide">Aucune vente en attente.</p>}
      <ul className="liste-attente">
        {(ventes.data ?? []).map((v) => (
          <li className="attente" key={v.id}>
            <div className="attente-tete">
              <span>
                {v.numero} · {dh(v.totaux.totalTtc)}
              </span>
              <span className="compte-a-rebours" title="Temps avant annulation automatique">
                {restant(v.attenteExpireLe, maintenant)}
              </span>
            </div>
            <span className="texte-secondaire">
              {v.lignes.map((l) => `${l.quantite} × ${l.designation}`).join(", ")}
              {v.noteAttente ? ` · ${v.noteAttente}` : ""}
            </span>
            <div className="rangee-boutons">
              <button
                type="button"
                className="bouton bouton-danger"
                disabled={abandonner.isPending}
                onClick={() => abandonner.mutate(v.id)}
              >
                Abandonner
              </button>
              <button
                type="button"
                className="bouton bouton-plein"
                disabled={!panierVide || reprendre.isPending}
                onClick={() => reprendre.mutate(v.id, { onSuccess: onReprise })}
              >
                Reprendre
              </button>
            </div>
          </li>
        ))}
      </ul>
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
