import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { IconeUtilisateur } from "../ui/icones";
import { useCreerUtilisateur, useModifierUtilisateur } from "./requetes";

type Utilisateur = Schemas["UtilisateurDto"];

// Règles indicatives pour guider la saisie ; le serveur reste seul juge (PolitiqueSecrets).
const commun = {
  nom: z.string().trim().min(1, "Nom obligatoire").max(80, "80 caractères maximum"),
  prenom: z.string().trim().max(80, "80 caractères maximum"),
  role: z.enum(["CAISSIER", "RESPONSABLE", "ADMIN"]),
  actif: z.boolean(),
  caisseIds: z.array(z.number()),
};

const schemaModification = z.object({ ...commun, login: z.string(), motDePasse: z.string() });

const schemaCreation = z
  .object({
    ...commun,
    login: z
      .string()
      .trim()
      .min(1, "Identifiant obligatoire")
      .max(60, "60 caractères maximum")
      .regex(/^[A-Za-z0-9._-]+$/, "Lettres sans accent, chiffres, point, tiret ou tiret bas"),
    motDePasse: z.string().min(8, "8 caractères minimum").max(72, "72 caractères maximum"),
  })
  .refine((c) => c.motDePasse.toLowerCase() !== c.login.toLowerCase(), {
    path: ["motDePasse"],
    message: "Doit être différent de l'identifiant",
  });

type Champs = z.infer<typeof schemaModification>;

interface Props {
  /** null : création d'un compte. */
  utilisateur: Utilisateur | null;
  caisses: Schemas["CaisseDto"][];
  /** Tous les comptes, pour savoir quelles caisses sont déjà attribuées. */
  autres: Utilisateur[];
  onFermer: () => void;
}

/** Création ou modification d'un compte : identité, rôle, état et caisse attribuée (une au plus). */
export function UtilisateurDialogue({ utilisateur, caisses, autres, onFermer }: Props) {
  const creer = useCreerUtilisateur();
  const modifier = useModifierUtilisateur();
  const mutation = utilisateur ? modifier : creer;
  const estAdmin = utilisateur?.role === "ADMIN";
  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors },
  } = useForm<Champs>({
    resolver: zodResolver(utilisateur ? schemaModification : schemaCreation),
    defaultValues: {
      nom: utilisateur?.nom ?? "",
      prenom: utilisateur?.prenom ?? "",
      login: utilisateur?.login ?? "",
      motDePasse: "",
      role: utilisateur?.role ?? "CAISSIER",
      actif: utilisateur?.actif ?? true,
      caisseIds: utilisateur?.caisseIds ?? [],
    },
  });
  const [role, actif, caisseIds] = useWatch({ control, name: ["role", "actif", "caisseIds"] });
  // Une caisse inactive déjà attribuée reste affichée pour pouvoir la retirer.
  const proposees = caisses.filter((c) => c.active || caisseIds.includes(c.id));
  // Une caisse n'a qu'un titulaire : celles des autres comptes sont affichées mais pas sélectionnables.
  const titulaire = (caisseId: number) =>
    autres.find((u) => u.id !== utilisateur?.id && u.role !== "ADMIN" && u.caisseIds.includes(caisseId));

  const envoyer = handleSubmit((c) => {
    if (utilisateur) {
      modifier.mutate(
        {
          id: utilisateur.id,
          nom: c.nom,
          prenom: c.prenom,
          role: c.role,
          actif: c.actif,
          caisseIds: c.caisseIds,
          version: utilisateur.version,
        },
        { onSuccess: onFermer },
      );
    } else {
      creer.mutate(
        {
          nom: c.nom,
          prenom: c.prenom,
          login: c.login,
          role: c.role,
          motDePasseTemporaire: c.motDePasse,
          caisseIds: c.caisseIds,
        },
        { onSuccess: onFermer },
      );
    }
  });

  return (
    <Dialogue
      titre={utilisateur ? `Modifier ${utilisateur.login}` : "Nouvel utilisateur"}
      icone={<IconeUtilisateur />}
      onFermer={onFermer}
      largeur={560}
    >
      <form className="formulaire" onSubmit={envoyer} noValidate>
        <div className="deux-colonnes">
          <label className="champ">
            <span className="libelle">Prénom</span>
            <input autoFocus {...register("prenom")} />
            {errors.prenom && <span className="erreur-champ">{errors.prenom.message}</span>}
          </label>
          <label className="champ">
            <span className="libelle">Nom</span>
            <input {...register("nom")} />
            {errors.nom && <span className="erreur-champ">{errors.nom.message}</span>}
          </label>
        </div>
        {!utilisateur && (
          <div className="deux-colonnes">
            <label className="champ">
              <span className="libelle">Identifiant</span>
              <input autoComplete="off" autoCapitalize="none" spellCheck={false} {...register("login")} />
              {errors.login && <span className="erreur-champ">{errors.login.message}</span>}
            </label>
            <label className="champ">
              <span className="libelle">Mot de passe temporaire</span>
              <input type="password" autoComplete="new-password" {...register("motDePasse")} />
              {errors.motDePasse && <span className="erreur-champ">{errors.motDePasse.message}</span>}
            </label>
          </div>
        )}
        {!estAdmin && (
          <>
            <div className="champ">
              <span className="libelle">Rôle</span>
              <div className="choix" role="group" aria-label="Rôle">
                <button
                  type="button"
                  aria-pressed={role === "CAISSIER"}
                  onClick={() => setValue("role", "CAISSIER")}
                >
                  Caissier
                </button>
                <button
                  type="button"
                  aria-pressed={role === "RESPONSABLE"}
                  onClick={() => setValue("role", "RESPONSABLE")}
                >
                  Responsable
                </button>
              </div>
            </div>
            {utilisateur && (
              <div className="champ">
                <span className="libelle">État du compte</span>
                <div className="choix" role="group" aria-label="État du compte">
                  <button type="button" aria-pressed={actif} onClick={() => setValue("actif", true)}>
                    Actif
                  </button>
                  <button type="button" aria-pressed={!actif} onClick={() => setValue("actif", false)}>
                    Désactivé
                  </button>
                </div>
              </div>
            )}
            <fieldset className="champ cases">
              <legend className="libelle">Caisse attribuée</legend>
              {proposees.length === 0 ? (
                <span className="texte-secondaire">
                  Aucune caisse active : créez-en une dans l'onglet Caisses.
                </span>
              ) : (
                <>
                  <label className="case">
                    <input
                      type="radio"
                      name="caisse"
                      checked={caisseIds.length === 0}
                      onChange={() => setValue("caisseIds", [])}
                    />
                    Aucune
                  </label>
                  {proposees.map((c) => {
                    const prise = titulaire(c.id);
                    return (
                      <label key={c.id} className={`case${prise ? " indisponible" : ""}`}>
                        <input
                          type="radio"
                          name="caisse"
                          checked={caisseIds.includes(c.id)}
                          disabled={prise !== undefined}
                          onChange={() => setValue("caisseIds", [c.id])}
                        />
                        {c.nom}
                        {!c.active && " (inactive)"}
                        {prise && (
                          <span className="texte-secondaire">
                            {" "}
                            · attribuée à {`${prise.prenom} ${prise.nom}`.trim()}
                          </span>
                        )}
                      </label>
                    );
                  })}
                </>
              )}
            </fieldset>
          </>
        )}
        {!utilisateur && (
          <p className="texte-secondaire">
            Le mot de passe temporaire devra être changé à la première connexion.
          </p>
        )}
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
