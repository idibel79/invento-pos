import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { messageErreur } from "../api/erreurs";
import { Logo } from "../ui/Logo";
import { useAuth } from "./AuthContext";

const schema = z.object({
  login: z.string().trim().min(1, "Identifiant obligatoire"),
  motDePasse: z.string().min(1, "Mot de passe obligatoire"),
});
type Champs = z.infer<typeof schema>;

export function Connexion() {
  const { connecter } = useAuth();
  const [erreur, setErreur] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Champs>({ resolver: zodResolver(schema) });

  const envoyer = handleSubmit(async ({ login, motDePasse }) => {
    setErreur(null);
    try {
      await connecter(login, motDePasse);
    } catch (e) {
      setErreur(messageErreur(e));
    }
  });

  return (
    <main className="page-centree">
      <form className="carte carte-formulaire" onSubmit={envoyer} noValidate>
        <Logo />
        <h1 className="titre-popup">Connexion à la caisse</h1>
        <label className="champ">
          <span className="libelle">Identifiant</span>
          <input autoComplete="username" autoFocus {...register("login")} />
          {errors.login && <span className="erreur-champ">{errors.login.message}</span>}
        </label>
        <label className="champ">
          <span className="libelle">Mot de passe</span>
          <input type="password" autoComplete="current-password" {...register("motDePasse")} />
          {errors.motDePasse && <span className="erreur-champ">{errors.motDePasse.message}</span>}
        </label>
        {erreur && (
          <p className="alerte alerte-danger" role="alert">
            {erreur}
          </p>
        )}
        <div className="rangee-boutons">
          <button type="submit" className="bouton bouton-plein" disabled={isSubmitting}>
            {isSubmitting ? "Connexion…" : "Se connecter"}
          </button>
        </div>
      </form>
    </main>
  );
}
