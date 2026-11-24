import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { api, type Schemas } from "../api/client";
import { appel, messageErreur } from "../api/erreurs";
import { Logo } from "../ui/Logo";

// Règle indicative pour guider la saisie ; le serveur reste seul juge (PolitiqueSecrets).
const schema = z
  .object({
    codeSecours: z.string().trim().min(1, "Code de secours obligatoire"),
    nouveau: z.string().min(8, "8 caractères minimum"),
    confirmation: z.string(),
  })
  .refine((c) => c.nouveau === c.confirmation, {
    path: ["confirmation"],
    message: "Les deux saisies diffèrent",
  });
type Champs = z.infer<typeof schema>;

async function recupererAdmin(codeSecours: string, nouveauMotDePasse: string) {
  return appel(api.POST("/api/v1/auth/secours", { body: { codeSecours, nouveauMotDePasse } }));
}

/**
 * Mot de passe admin oublié ou compte admin verrouillé : le code de secours redéfinit le mot de
 * passe, déverrouille le compte, puis un nouveau code est affiché une seule fois.
 */
export function RecuperationAdmin({ onRetour }: { onRetour: (login?: string) => void }) {
  const [erreur, setErreur] = useState<string | null>(null);
  const [resultat, setResultat] = useState<Schemas["RecuperationDto"] | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Champs>({ resolver: zodResolver(schema) });

  const envoyer = handleSubmit(async ({ codeSecours, nouveau }) => {
    setErreur(null);
    try {
      setResultat(await recupererAdmin(codeSecours, nouveau));
    } catch (e) {
      setErreur(messageErreur(e));
    }
  });

  if (resultat) {
    return (
      <main className="page-centree">
        <div className="carte carte-formulaire">
          <Logo />
          <h1 className="titre-popup">Mot de passe redéfini</h1>
          <p className="texte-secondaire">
            Le compte {resultat.login} est déverrouillé. L'ancien code de secours ne fonctionne plus : notez
            le nouveau et rangez-le hors de l'ordinateur de la caisse.
          </p>
          <div className="encadre">
            <span className="libelle">Nouveau code de secours, à noter maintenant</span>
            <code className="code-secours" data-testid="code-secours">
              {resultat.nouveauCodeSecours}
            </code>
          </div>
          <p className="texte-secondaire">Ce code ne sera plus jamais affiché.</p>
          <div className="rangee-boutons">
            <button
              type="button"
              className="bouton bouton-plein"
              autoFocus
              onClick={() => onRetour(resultat.login)}
            >
              Je l'ai noté, me connecter
            </button>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="page-centree">
      <form className="carte carte-formulaire" onSubmit={envoyer} noValidate>
        <Logo />
        <h1 className="titre-popup">Code de secours administrateur</h1>
        <p className="texte-secondaire">
          Saisissez le code de secours remis à l'installation et choisissez un nouveau mot de passe. Le compte
          admin sera aussi déverrouillé.
        </p>
        <label className="champ">
          <span className="libelle">Code de secours</span>
          <input autoComplete="off" autoFocus spellCheck={false} {...register("codeSecours")} />
          {errors.codeSecours && <span className="erreur-champ">{errors.codeSecours.message}</span>}
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
          <button type="button" className="bouton" onClick={() => onRetour()}>
            Retour
          </button>
          <button type="submit" className="bouton bouton-plein" disabled={isSubmitting}>
            {isSubmitting ? "Validation…" : "Redéfinir le mot de passe"}
          </button>
        </div>
      </form>
    </main>
  );
}
