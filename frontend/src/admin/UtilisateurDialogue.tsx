import { zodResolver } from "@hookform/resolvers/zod";
import { useId, useState, type ComponentProps } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { lireMontant } from "../ui/format";
import { IconeOeil, IconeOeilBarre, IconeUtilisateur } from "../ui/icones";
import { useCreerUtilisateur, useModifierUtilisateur } from "./requetes";

type Utilisateur = Schemas["UtilisateurDto"];

// Règles indicatives pour guider la saisie ; le serveur reste seul juge (PolitiqueSecrets, SeuilsRemise).
const motDePasse = z.string().min(8, "8 caractères minimum").max(72, "72 caractères maximum");
const seuil = z.string().refine((v) => {
  const n = lireMontant(v);
  return n !== null && n <= 100;
}, "Entre 0 et 100 %");

const commun = {
  nom: z.string().trim().min(1, "Nom obligatoire").max(80, "80 caractères maximum"),
  prenom: z.string().trim().max(80, "80 caractères maximum"),
  login: z
    .string()
    .trim()
    .min(1, "Identifiant obligatoire")
    .max(60, "60 caractères maximum")
    .regex(/^[A-Za-z0-9._-]+$/, "Lettres sans accent, chiffres, point, tiret ou tiret bas"),
  // PIN facultatif : vide, il n'est pas défini (création) ou reste inchangé (modification).
  pin: z.string().regex(/^(\d{4})?$/, "Exactement 4 chiffres"),
  seuilLigne: seuil,
  seuilTicket: seuil,
  role: z.enum(["CAISSIER", "RESPONSABLE", "ADMIN"]),
  actif: z.boolean(),
  caisseIds: z.array(z.number()),
};

const differentDuLogin = {
  check: (c: { login: string; motDePasse: string }) => c.motDePasse.toLowerCase() !== c.login.toLowerCase(),
  options: { path: ["motDePasse"], message: "Doit être différent de l'identifiant" },
};

const schemaCreation = z
  .object({ ...commun, motDePasse })
  .refine(differentDuLogin.check, differentDuLogin.options);

// En modification, un mot de passe vide reste inchangé.
const schemaModification = z
  .object({
    ...commun,
    motDePasse: z
      .string()
      .max(72, "72 caractères maximum")
      .refine((v) => v === "" || v.length >= 8, "8 caractères minimum"),
  })
  .refine(differentDuLogin.check, differentDuLogin.options);

type Champs = z.infer<typeof schemaModification>;

const versSaisie = (n: number | undefined) => String(n ?? 0).replace(".", ",");

interface Props {
  /** null : création d'un compte. */
  utilisateur: Utilisateur | null;
  caisses: Schemas["CaisseDto"][];
  /** Tous les comptes, pour savoir quelles caisses sont déjà attribuées. */
  autres: Utilisateur[];
  onFermer: () => void;
}

/**
 * Création ou modification d'un compte : identité, identifiant, mot de passe, rôle, état et caisse
 * attribuée (une au plus). Un responsable a un PIN et des seuils de remise fixés par l'admin ; un
 * caissier n'accorde aucune remise et n'a pas de PIN.
 */
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
      pin: "",
      seuilLigne: versSaisie(utilisateur?.seuilsRemise.ligne),
      seuilTicket: versSaisie(utilisateur?.seuilsRemise.ticket),
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
  const pinActuel = utilisateur?.pinDefini === true && utilisateur.role !== "CAISSIER";

  const envoyer = handleSubmit((c) => {
    const pin = c.role === "CAISSIER" || c.pin === "" ? null : c.pin;
    const seuilsRemise =
      c.role === "RESPONSABLE"
        ? { ligne: lireMontant(c.seuilLigne) ?? 0, ticket: lireMontant(c.seuilTicket) ?? 0 }
        : { ligne: 0, ticket: 0 };
    if (utilisateur) {
      modifier.mutate(
        {
          id: utilisateur.id,
          nom: c.nom,
          prenom: c.prenom,
          login: c.login,
          motDePasseTemporaire: c.motDePasse || null,
          pin,
          role: c.role,
          actif: c.actif,
          caisseIds: c.caisseIds,
          seuilsRemise,
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
          pin,
          seuilsRemise,
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
        <div className="deux-colonnes">
          <label className="champ">
            <span className="libelle">Identifiant</span>
            <input autoComplete="off" autoCapitalize="none" spellCheck={false} {...register("login")} />
            {errors.login && <span className="erreur-champ">{errors.login.message}</span>}
          </label>
          <ChampSecret
            libelle={utilisateur ? "Nouveau mot de passe temporaire" : "Mot de passe temporaire"}
            nom="le mot de passe"
            erreur={errors.motDePasse?.message}
            placeholder={utilisateur ? "Inchangé si vide" : undefined}
            {...register("motDePasse")}
          />
        </div>
        {!estAdmin && (
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
        )}
        {role !== "CAISSIER" && (
          <div className="deux-colonnes">
            <ChampSecret
              libelle={pinActuel ? "Nouveau PIN" : "PIN"}
              nom="le PIN"
              erreur={errors.pin?.message}
              inputMode="numeric"
              maxLength={4}
              placeholder={pinActuel ? "Inchangé si vide" : "Aucun PIN défini"}
              {...register("pin")}
            />
          </div>
        )}
        {role === "RESPONSABLE" && (
          <div className="deux-colonnes">
            <label className="champ">
              <span className="libelle">Remise max par ligne (%)</span>
              <input inputMode="decimal" autoComplete="off" {...register("seuilLigne")} />
              {errors.seuilLigne && <span className="erreur-champ">{errors.seuilLigne.message}</span>}
            </label>
            <label className="champ">
              <span className="libelle">Remise max sur le ticket (%)</span>
              <input inputMode="decimal" autoComplete="off" {...register("seuilTicket")} />
              {errors.seuilTicket && <span className="erreur-champ">{errors.seuilTicket.message}</span>}
            </label>
          </div>
        )}
        <p className="texte-secondaire">
          {utilisateur
            ? "Un nouveau mot de passe est temporaire : il devra être changé à la prochaine connexion ; le compte est déverrouillé et déconnecté de tous les postes."
            : "Le mot de passe temporaire devra être changé à la première connexion."}{" "}
          {role === "CAISSIER"
            ? "Un caissier n'accorde aucune remise : un responsable l'autorise avec son PIN."
            : role === "RESPONSABLE"
              ? "Le responsable accorde les remises dans ses seuils, sur sa caisse ou avec son PIN sur celle d'un caissier ; au-delà, il faut l'admin."
              : "Le PIN de l'admin autorise toute remise."}
        </p>
        {!estAdmin && (
          <>
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

type PropsSecret = Omit<ComponentProps<"input">, "type"> & {
  libelle: string;
  /** Pour le bouton œil : « Afficher le PIN ». */
  nom: string;
  erreur: string | undefined;
};

/** Champ masqué par défaut, avec un bouton œil pour vérifier la saisie avant d'enregistrer. */
function ChampSecret({ libelle, nom, erreur, ...champ }: PropsSecret) {
  const [visible, setVisible] = useState(false);
  const id = useId();
  return (
    <div className="champ">
      <label className="libelle" htmlFor={id}>
        {libelle}
      </label>
      <div className="champ-secret">
        <input
          id={id}
          autoComplete="new-password"
          spellCheck={false}
          {...champ}
          type={visible ? "text" : "password"}
        />
        <button
          type="button"
          aria-label={`${visible ? "Masquer" : "Afficher"} ${nom}`}
          aria-pressed={visible}
          onClick={() => setVisible((v) => !v)}
        >
          {visible ? <IconeOeilBarre /> : <IconeOeil />}
        </button>
      </div>
      {erreur && <span className="erreur-champ">{erreur}</span>}
    </div>
  );
}
