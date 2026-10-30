import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useCaisses } from "../caisse/requetes";
import { Dialogue } from "../ui/Dialogue";
import { IconeCaisse } from "../ui/icones";
import { EtatRequete } from "./EtatRequete";
import { useEnregistrerCaisse, useUtilisateurs } from "./requetes";

type Caisse = Schemas["CaisseDto"];

/** Caisses du magasin ; l'attribution aux utilisateurs se fait depuis leur fiche. */
export function Caisses() {
  const caisses = useCaisses();
  const utilisateurs = useUtilisateurs();
  const [edition, setEdition] = useState<Caisse | "nouvelle" | null>(null);

  const attributaires = (caisse: Caisse) =>
    (utilisateurs.data ?? [])
      .filter((u) => u.role !== "ADMIN" && u.caisseIds.includes(caisse.id))
      .map((u) => `${u.prenom} ${u.nom}`.trim());

  return (
    <section className="admin-section">
      <div className="admin-tete">
        <div>
          <h1 className="admin-titre">Caisses</h1>
          <p className="texte-secondaire">
            Une caisse inactive ne peut plus être ouverte. L'admin peut ouvrir toutes les caisses actives ;
            les autres utilisateurs, celles qui leur sont attribuées (onglet Utilisateurs).
          </p>
        </div>
        <button type="button" className="bouton bouton-plein" onClick={() => setEdition("nouvelle")}>
          Nouvelle caisse
        </button>
      </div>
      <EtatRequete requete={caisses} vide="Aucune caisse. Créez la première pour pouvoir ouvrir une session.">
        {(liste) => (
          <ul className="liste-admin">
            {liste.map((caisse) => {
              const noms = attributaires(caisse);
              return (
                <li key={caisse.id} className="ligne-admin">
                  <div className="ligne-admin-texte">
                    <span className="ligne-admin-nom">
                      {caisse.nom}{" "}
                      <span className={`statut ${caisse.active ? "statut-ok" : "statut-gris"}`}>
                        {caisse.active ? "Active" : "Inactive"}
                      </span>
                    </span>
                    <span className="texte-secondaire">
                      {noms.length > 0 ? `Attribuée à ${noms.join(", ")}` : "Attribuée à aucun utilisateur"}
                    </span>
                  </div>
                  <button type="button" className="bouton-petit" onClick={() => setEdition(caisse)}>
                    Modifier
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </EtatRequete>
      {edition && (
        <CaisseDialogue caisse={edition === "nouvelle" ? null : edition} onFermer={() => setEdition(null)} />
      )}
    </section>
  );
}

const schema = z.object({
  nom: z.string().trim().min(1, "Nom obligatoire").max(80, "80 caractères maximum"),
  active: z.boolean(),
});
type Champs = z.infer<typeof schema>;

function CaisseDialogue({ caisse, onFermer }: { caisse: Caisse | null; onFermer: () => void }) {
  const enregistrer = useEnregistrerCaisse();
  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors },
  } = useForm<Champs>({
    resolver: zodResolver(schema),
    defaultValues: { nom: caisse?.nom ?? "", active: caisse?.active ?? true },
  });
  const active = useWatch({ control, name: "active" });

  const envoyer = handleSubmit((champs) =>
    enregistrer.mutate({ id: caisse?.id ?? null, ...champs }, { onSuccess: onFermer }),
  );

  return (
    <Dialogue
      titre={caisse ? `Modifier ${caisse.nom}` : "Nouvelle caisse"}
      icone={<IconeCaisse />}
      onFermer={onFermer}
    >
      <form className="formulaire" onSubmit={envoyer} noValidate>
        <label className="champ">
          <span className="libelle">Nom</span>
          <input autoFocus placeholder="Caisse 1" {...register("nom")} />
          {errors.nom && <span className="erreur-champ">{errors.nom.message}</span>}
        </label>
        <div className="champ">
          <span className="libelle">État</span>
          <div className="choix" role="group" aria-label="État">
            <button type="button" aria-pressed={active} onClick={() => setValue("active", true)}>
              Active
            </button>
            <button type="button" aria-pressed={!active} onClick={() => setValue("active", false)}>
              Inactive
            </button>
          </div>
        </div>
        {enregistrer.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(enregistrer.error)}
          </p>
        )}
        <div className="rangee-boutons">
          <button type="button" className="bouton bouton-contour" onClick={onFermer}>
            Annuler
          </button>
          <button type="submit" className="bouton bouton-plein" disabled={enregistrer.isPending}>
            Enregistrer
          </button>
        </div>
      </form>
    </Dialogue>
  );
}
