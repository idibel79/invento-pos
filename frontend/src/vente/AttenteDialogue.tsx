import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { IconeHorloge } from "../ui/icones";
import { useMettreEnAttente } from "./requetes";

interface Props {
  commande: Schemas["PanierCommande"];
  onMiseEnAttente: (vente: Schemas["VenteDto"]) => void;
  onFermer: () => void;
}

/** Le client part chercher de quoi payer : panier gardé, stock réservé 5 minutes. */
export function AttenteDialogue({ commande, onMiseEnAttente, onFermer }: Props) {
  const [cle] = useState(() => crypto.randomUUID());
  const [note, setNote] = useState("");
  const attente = useMettreEnAttente();

  return (
    <Dialogue titre="Mettre la vente en attente" icone={<IconeHorloge />} onFermer={onFermer}>
      <p className="texte-secondaire">
        Le panier est conservé et le stock réservé pendant 5 minutes. Passé ce délai, ou à la clôture de la
        caisse, la vente est annulée. Elle se reprend uniquement sur cette caisse.
      </p>
      <label className="champ">
        <span className="libelle">Note (facultative)</span>
        <textarea
          maxLength={255}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Ex. : parti au distributeur"
          autoFocus
        />
      </label>
      {attente.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(attente.error)}
        </p>
      )}
      <div className="rangee-boutons">
        <button type="button" className="bouton bouton-contour" onClick={onFermer}>
          Annuler
        </button>
        <button
          type="button"
          className="bouton bouton-plein"
          disabled={attente.isPending}
          onClick={() =>
            attente.mutate(
              { cle, corps: { panier: commande, note: note.trim() || undefined } },
              { onSuccess: onMiseEnAttente },
            )
          }
        >
          Mettre en attente
        </button>
      </div>
    </Dialogue>
  );
}
