import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { messageErreur } from "../api/erreurs";
import { Logo } from "../ui/Logo";
import { useAuth } from "./AuthContext";

// Règle indicative pour guider la saisie ; le serveur reste seul juge (PolitiqueSecrets).
const schema = z
  .object({
    actuel: z.string().min(1, "Mot de passe actuel obligatoire"),
    nouveau: z.string().min(8, "8 caractères minimum"),
    confirmation: z.string(),
  })
  .refine((c) => c.nouveau === c.confirmation, {
    path: ["confirmation"],
    message: "Les deux saisies diffèrent",
  });
type Champs = z.infer<typeof schema>;

export function ChangementMotDePasse() {
  const { changerMotDePasse, deconnecter } = useAuth();
  const [erreur, setErreur] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Champs>({ resolver: zodResolver(schema) });

  const envoyer = handleSubmit(async ({ actuel, nouveau }) => {
    setErreur(null);
    try {
      await changerMotDePasse(actuel, nouveau);
    } catch (e) {
      setErreur(messageErreur(e));
    }
  });

  return (
    <main className="page-centree">
      <form className="carte carte-formulaire" onSubmit={envoyer} noValidate>
        <Logo />
        <h1 className="titre-popup">Choisissez votre mot de passe</h1>
        <p className="texte-secondaire">Le mot de passe temporaire doit être remplacé avant de continuer.</p>
        <label className="champ">
          <span className="libelle">Mot de passe actuel</span>
          <input type="password" autoComplete="current-password" autoFocus {...register("actuel")} />
          {errors.actuel && <span className="erreur-champ">{errors.actuel.message}</span>}
        </label>
        <label className="champ">
          <span className="libelle">Nouveau mot de passe</span>
          <input type="password" autoComplete="new-password" {...register("nouveau")} />
          {errors.nouveau && <span className="erreur-champ">{errors.nouveau.message}</span>}
        </label>
        <label className="champ">
          <span className="libelle">Confirmation</span>
          <input type="password" autoComplete="new-password" {...register("confirmation")} />
          {errors.confirmation && <span className="erreur-champ">{errors.confirmation.message}</span>}
        </label>
        {erreur && (
          <p className="alerte alerte-danger" role="alert">
            {erreur}
          </p>
        )}
        <div className="rangee-boutons">
          <button type="button" className="bouton bouton-contour" onClick={() => void deconnecter()}>
            Annuler
          </button>
          <button type="submit" className="bouton bouton-plein" disabled={isSubmitting}>
            Enregistrer
          </button>
        </div>
      </form>
    </main>
  );
}
