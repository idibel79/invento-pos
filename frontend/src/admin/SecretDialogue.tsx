import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { IconeCle } from "../ui/icones";
import { useDefinirPin, useReinitialiserMotDePasse } from "./requetes";

const schemas = {
  "mot-de-passe": z
    .object({
      valeur: z.string().min(8, "8 caractères minimum").max(72, "72 caractères maximum"),
      confirmation: z.string(),
    })
    .refine((c) => c.valeur === c.confirmation, {
      path: ["confirmation"],
      message: "Les deux saisies diffèrent",
    }),
  pin: z
    .object({ valeur: z.string().regex(/^\d{4}$/, "Exactement 4 chiffres"), confirmation: z.string() })
    .refine((c) => c.valeur === c.confirmation, {
      path: ["confirmation"],
      message: "Les deux saisies diffèrent",
    }),
};
type Champs = { valeur: string; confirmation: string };

interface Props {
  type: "mot-de-passe" | "pin";
  utilisateur: Schemas["UtilisateurDto"];
  onFermer: () => void;
}

/** Mot de passe temporaire (déverrouille le compte) ou PIN d'autorisation défini par l'admin. */
export function SecretDialogue({ type, utilisateur, onFermer }: Props) {
  const reinitialiser = useReinitialiserMotDePasse();
  const definirPin = useDefinirPin();
  const mutation = type === "pin" ? definirPin : reinitialiser;
  const pin = type === "pin";
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Champs>({
    resolver: zodResolver(schemas[type]),
    defaultValues: { valeur: "", confirmation: "" },
  });

  const envoyer = handleSubmit(({ valeur }) => {
    if (pin) definirPin.mutate({ id: utilisateur.id, pin: valeur }, { onSuccess: onFermer });
    else reinitialiser.mutate({ id: utilisateur.id, motDePasseTemporaire: valeur }, { onSuccess: onFermer });
  });

  const champ = {
    type: "password",
    autoComplete: "new-password",
    ...(pin ? { inputMode: "numeric" as const, maxLength: 4 } : {}),
  };

  return (
    <Dialogue
      titre={`${pin ? "PIN" : "Mot de passe"} de ${utilisateur.prenom || utilisateur.login}`}
      icone={<IconeCle />}
      onFermer={onFermer}
    >
      <form className="formulaire" onSubmit={envoyer} noValidate>
        <p className="texte-secondaire">
          {pin
            ? `${utilisateur.pinDefini ? "Remplace le PIN actuel. " : ""}Le PIN sert à autoriser une remise au-delà du plafond du caissier.`
            : "Mot de passe temporaire, à changer à la prochaine connexion. Le compte est déverrouillé et déconnecté de tous les postes."}
        </p>
        <label className="champ">
          <span className="libelle">{pin ? "Nouveau PIN" : "Mot de passe temporaire"}</span>
          <input autoFocus {...champ} {...register("valeur")} />
          {errors.valeur && <span className="erreur-champ">{errors.valeur.message}</span>}
        </label>
        <label className="champ">
          <span className="libelle">Confirmation</span>
          <input {...champ} {...register("confirmation")} />
          {errors.confirmation && <span className="erreur-champ">{errors.confirmation.message}</span>}
        </label>
        {mutation.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(mutation.error)}
          </p>
        )}
        <div className="rangee-boutons">
          <button type="button" className="bouton bouton-contour" onClick={onFermer}>
            Annuler
          </button>
          <button type="submit" className="bouton bouton-plein" disabled={mutation.isPending}>
            Enregistrer
          </button>
        </div>
      </form>
    </Dialogue>
  );
}
