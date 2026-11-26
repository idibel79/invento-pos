import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { nouvelleCle, useRecevoirDe } from "../tresorerie/requetes";
import { Dialogue } from "../ui/Dialogue";
import { dh, lireMontant } from "../ui/format";

const montant = z.string().refine((v) => v.trim() === "" || (lireMontant(v) ?? -1) >= 0, "Montant invalide");
const schema = z
  .object({ especes: montant, tpe: montant })
  .refine((c) => (lireMontant(c.especes) ?? 0) + (lireMontant(c.tpe) ?? 0) > 0, {
    message: "Indiquez un montant",
    path: ["especes"],
  });
type Champs = z.infer<typeof schema>;

/** Valeur de départ d'un champ : ce qui est dû dans ce mode, vide s'il n'y a rien. */
function depart(du: number): string {
  return du > 0 ? du.toFixed(2).replace(".", ",") : "";
}

/**
 * Argent qu'un utilisateur désactivé remet à l'admin : il ne peut plus verser lui-même, l'admin l'enregistre et
 * le reçoit aussitôt dans la caisse principale. Le serveur le limite à ce qui est dû dans chaque mode.
 */
export function RecevoirDialogue({
  compte,
  onFermer,
}: {
  compte: Schemas["CompteCaissierDto"];
  onFermer: () => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Champs>({
    resolver: zodResolver(schema),
    defaultValues: { especes: depart(compte.soldeEspeces), tpe: depart(compte.soldeTpe) },
  });
  const [cle] = useState(nouvelleCle);
  const recevoir = useRecevoirDe();

  const envoyer = handleSubmit((c) =>
    recevoir.mutate(
      {
        utilisateurId: compte.utilisateurId,
        montantEspeces: lireMontant(c.especes) ?? 0,
        montantTpe: lireMontant(c.tpe) ?? 0,
        cle,
      },
      { onSuccess: onFermer },
    ),
  );

  return (
    <Dialogue titre={`Recevoir de ${compte.nom}`} onFermer={onFermer} largeur={460}>
      <form className="formulaire" onSubmit={envoyer} noValidate>
        <p className="texte-secondaire">
          Ce compte est désactivé : enregistrez ici l'argent qu'il vous remet. Il entre tout de suite dans la
          caisse principale et réduit ce qu'il doit ({dh(compte.solde)}).
        </p>
        <div className="deux-colonnes">
          <label className="champ">
            <span className="libelle">Espèces (DH)</span>
            <input
              inputMode="decimal"
              placeholder="0,00"
              autoComplete="off"
              autoFocus
              {...register("especes")}
            />
            <span className="texte-secondaire">Dû : {dh(compte.soldeEspeces)}</span>
            {errors.especes && <span className="erreur-champ">{errors.especes.message}</span>}
          </label>
          <label className="champ">
            <span className="libelle">Tickets TPE (DH)</span>
            <input inputMode="decimal" placeholder="0,00" autoComplete="off" {...register("tpe")} />
            <span className="texte-secondaire">Dû : {dh(compte.soldeTpe)}</span>
            {errors.tpe && <span className="erreur-champ">{errors.tpe.message}</span>}
          </label>
        </div>
        {recevoir.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(recevoir.error)}
          </p>
        )}
        <div className="rangee-boutons">
          <button type="button" className="bouton bouton-contour" onClick={onFermer}>
            Annuler
          </button>
          <button type="submit" className="bouton bouton-plein" disabled={recevoir.isPending}>
            Recevoir
          </button>
        </div>
      </form>
    </Dialogue>
  );
}
