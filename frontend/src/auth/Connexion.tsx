import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { ErreurApi, messageErreur } from "../api/erreurs";
import { Logo } from "../ui/Logo";
import { useAuth } from "./AuthContext";
import { RecuperationAdmin } from "./RecuperationAdmin";

const schema = z.object({
  login: z.string().trim().min(1, "Identifiant ou prénom et nom obligatoire"),
  motDePasse: z.string().min(1, "Mot de passe obligatoire"),
});
type Champs = z.infer<typeof schema>;

export function Connexion() {
  const [secours, setSecours] = useState(false);
  const [login, setLogin] = useState("");

  if (secours) {
    return (
      <RecuperationAdmin
        onRetour={(loginAdmin) => {
          if (loginAdmin) setLogin(loginAdmin);
          setSecours(false);
        }}
      />
    );
  }
  return <FormulaireConnexion loginInitial={login} onSecours={() => setSecours(true)} />;
}

function FormulaireConnexion({ loginInitial, onSecours }: { loginInitial: string; onSecours: () => void }) {
  const { connecter } = useAuth();
  const [erreur, setErreur] = useState<string | null>(null);
  // Le lien vers le code de secours n'apparaît qu'une fois le compte verrouillé (HTTP 403).
  const [verrouille, setVerrouille] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Champs>({ resolver: zodResolver(schema), defaultValues: { login: loginInitial } });

  const envoyer = handleSubmit(async ({ login, motDePasse }) => {
    setErreur(null);
    try {
      await connecter(login, motDePasse);
    } catch (e) {
      setErreur(messageErreur(e));
      setVerrouille(e instanceof ErreurApi && e.statut === 403);
    }
  });

  return (
    <main className="page-centree">
      <form className="carte carte-formulaire" onSubmit={envoyer} noValidate>
        <Logo />
        <h1 className="titre-popup">Connexion à la caisse</h1>
        <label className="champ">
          <span className="libelle">Identifiant</span>
          <input
            autoComplete="username"
            autoFocus={!loginInitial}
            placeholder="Identifiant ou prénom et nom"
            {...register("login")}
          />
          {errors.login && <span className="erreur-champ">{errors.login.message}</span>}
        </label>
        <label className="champ">
          <span className="libelle">Mot de passe</span>
          <input
            type="password"
            autoComplete="current-password"
            autoFocus={!!loginInitial}
            {...register("motDePasse")}
          />
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
        {verrouille && (
          <button type="button" className="bouton-lien bouton-proposition" onClick={onSecours}>
            Administrateur : utiliser le code de secours
          </button>
        )}
      </form>
    </main>
  );
}
