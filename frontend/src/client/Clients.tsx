import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { decimal, dh, lireMontant } from "../ui/format";
import { EncaisserDetteDialogue } from "./EncaisserDetteDialogue";
import { ReleveDialogue } from "./Releve";
import { type Client, texteSolde, useClients, useEnregistrerClient, useSupprimerClient } from "./requetes";

type Popup =
  | { type: "nouveau" }
  | { type: "modifier"; client: Client }
  | { type: "releve"; client: Client }
  | { type: "dette"; client: Client }
  | { type: "supprimer"; client: Client };

/**
 * Paramètres › Clients (admin), design B : tableau pleine largeur ; un clic sur un client ouvre son relevé
 * en popup, chaque ligne a Encaisser (une dette) et Modifier (d'où l'on supprime). Seuls les clients de
 * l'annuaire peuvent payer à crédit.
 */
export function Clients() {
  const [recherche, setRecherche] = useState("");
  const [filtre, setFiltre] = useState<"tous" | "entreprises" | "dette" | "desactives">("tous");
  const clients = useClients(recherche, true);
  const [popup, setPopup] = useState<Popup | null>(null);

  const tous = clients.data ?? [];
  const actifs = tous.filter((c) => c.actif);
  const totalDettes = tous.filter((c) => c.dette > 0).reduce((somme, c) => somme + c.dette, 0);
  const liste = tous.filter((c) =>
    filtre === "dette"
      ? c.dette > 0
      : filtre === "desactives"
        ? !c.actif
        : filtre === "entreprises"
          ? c.actif && c.type === "ENTREPRISE"
          : c.actif,
  );
  const fermer = () => setPopup(null);

  return (
    <section className="admin-section clients-ecran">
      <div className="clients-tete">
        <h1 className="admin-titre">
          Clients{" "}
          <span className="texte-secondaire clients-resume">
            · {actifs.length} actifs · dettes {dh(totalDettes)}
          </span>
        </h1>
        <div className="clients-filtres" role="group" aria-label="Filtre">
          {(
            [
              ["tous", "Tous"],
              ["entreprises", "Entreprises"],
              ["dette", "Avec dette"],
              ["desactives", "Désactivés"],
            ] as const
          ).map(([valeur, libelle]) => (
            <button
              key={valeur}
              type="button"
              className="pastille-filtre"
              aria-pressed={filtre === valeur}
              onClick={() => setFiltre(valeur)}
            >
              {libelle}
            </button>
          ))}
        </div>
        <input
          type="search"
          className="clients-recherche"
          aria-label="Rechercher un client"
          placeholder="Nom, téléphone ou ICE…"
          value={recherche}
          onChange={(e) => setRecherche(e.target.value)}
        />
        <button type="button" className="bouton bouton-plein" onClick={() => setPopup({ type: "nouveau" })}>
          + Nouveau client
        </button>
      </div>
      {clients.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(clients.error)}
        </p>
      )}
      <div className="clients-tableau" role="table" aria-label="Clients">
        <div className="clients-ligne clients-entete" role="row">
          <span role="columnheader">Nom</span>
          <span role="columnheader">Téléphone</span>
          <span role="columnheader">Note</span>
          <span role="columnheader" className="a-droite">
            Dette
          </span>
          <span role="columnheader">
            <span className="visuellement-cache">Actions</span>
          </span>
        </div>
        {clients.isPending && <p className="vide">Chargement…</p>}
        {clients.isSuccess && liste.length === 0 && (
          <p className="vide">
            {recherche.trim() || filtre !== "tous"
              ? "Aucun client ne correspond."
              : "Aucun client. Ajoutez le premier."}
          </p>
        )}
        {liste.map((c) => (
          <div
            key={c.id}
            role="row"
            className={`clients-ligne cliquable${c.actif ? "" : " inactive"}`}
            title="Voir le relevé"
            onClick={() => setPopup({ type: "releve", client: c })}
          >
            <span role="cell" className="clients-nom">
              <span className="clients-nom-bloc">
                <button
                  type="button"
                  className="lien-nom"
                  onClick={(e) => {
                    e.stopPropagation();
                    setPopup({ type: "releve", client: c });
                  }}
                >
                  {c.nom}
                </button>
                {c.type === "ENTREPRISE" && detailEntreprise(c) && (
                  <span className="texte-secondaire clients-detail">{detailEntreprise(c)}</span>
                )}
              </span>
              {c.type === "ENTREPRISE" && <span className="statut statut-entreprise">Entreprise</span>}
              {!c.actif && <span className="statut statut-gris">Désactivé</span>}
            </span>
            <span role="cell" className="texte-secondaire">
              {c.telephone ?? "—"}
            </span>
            <span role="cell" className="texte-secondaire clients-note">
              {c.note ?? ""}
            </span>
            <span
              role="cell"
              className={`a-droite chiffres${c.dette > 0 ? " texte-dette" : " texte-secondaire"}`}
            >
              {texteSolde(c.dette)}
            </span>
            <span role="cell" className="clients-actions" onClick={(e) => e.stopPropagation()}>
              <button
                type="button"
                className="bouton bouton-plein bouton-petit"
                onClick={() => setPopup({ type: "dette", client: c })}
              >
                Encaisser
              </button>
              <button
                type="button"
                className="bouton bouton-contour bouton-petit"
                onClick={() => setPopup({ type: "modifier", client: c })}
              >
                Modifier
              </button>
            </span>
          </div>
        ))}
      </div>
      {(popup?.type === "nouveau" || popup?.type === "modifier") && (
        <ClientDialogue
          client={popup.type === "modifier" ? popup.client : null}
          onFermer={fermer}
          onSupprimer={
            popup.type === "modifier"
              ? () => setPopup({ type: "supprimer", client: popup.client })
              : undefined
          }
        />
      )}
      {popup?.type === "releve" && <ReleveDialogue client={popup.client} onFermer={fermer} />}
      {popup?.type === "dette" && <EncaisserDetteDialogue clientInitial={popup.client} onFermer={fermer} />}
      {popup?.type === "supprimer" && <SuppressionDialogue client={popup.client} onFermer={fermer} />}
    </section>
  );
}

/** Sous le nom d'une entreprise : son ICE et la personne à contacter, s'ils sont renseignés. */
function detailEntreprise(c: Client): string {
  return [c.ice && `ICE ${c.ice}`, c.contact && `contact ${c.contact}`].filter(Boolean).join(" · ");
}

/** Suppression ; un client qui a déjà acheté est seulement désactivé (le serveur décide). */
function SuppressionDialogue({ client, onFermer }: { client: Client; onFermer: () => void }) {
  const supprimer = useSupprimerClient();
  const [desactive, setDesactive] = useState(false);

  if (desactive) {
    return (
      <Dialogue titre={`${client.nom} est désactivé`} onFermer={onFermer}>
        <div className="formulaire">
          <p>
            Ce client a déjà un historique : il a été désactivé au lieu d'être supprimé. Il n'est plus proposé
            à la caisse ; son relevé et sa dette sont conservés. Modifier pour le réactiver.
          </p>
          <div className="rangee-boutons">
            <button type="button" className="bouton bouton-plein" onClick={onFermer}>
              Fermer
            </button>
          </div>
        </div>
      </Dialogue>
    );
  }

  return (
    <Dialogue titre={`Supprimer ${client.nom} ?`} onFermer={onFermer}>
      <div className="formulaire">
        <p>
          S'il n'a jamais rien acheté, il est supprimé de l'annuaire. Sinon il est seulement désactivé : il
          n'est plus proposé à la caisse, son relevé et sa dette sont conservés.
        </p>
        {supprimer.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(supprimer.error)}
          </p>
        )}
        <div className="rangee-boutons">
          <button type="button" className="bouton bouton-contour" onClick={onFermer}>
            Annuler
          </button>
          <button
            type="button"
            className="bouton bouton-danger"
            disabled={supprimer.isPending}
            onClick={() =>
              supprimer.mutate(client.id, {
                onSuccess: ({ supprime }) => (supprime ? onFermer() : setDesactive(true)),
              })
            }
          >
            Supprimer
          </button>
        </div>
      </div>
    </Dialogue>
  );
}

// Règles indicatives pour guider la saisie ; le serveur reste seul juge.
const schema = z.object({
  type: z.enum(["PARTICULIER", "ENTREPRISE"]),
  nom: z.string().trim().min(1, "Obligatoire").max(120, "120 caractères maximum"),
  telephone: z.string().trim().max(30, "30 caractères maximum"),
  note: z.string().trim().max(300, "300 caractères maximum"),
  plafond: z
    .string()
    .trim()
    .refine((v) => v === "" || (lireMontant(v) ?? 0) > 0, "Montant invalide (supérieur à 0)"),
  actif: z.boolean(),
  ice: z
    .string()
    .trim()
    .refine((v) => v === "" || /^\d{15}$/.test(v.replace(/\s/g, "")), "L'ICE compte 15 chiffres"),
  identifiantFiscal: z.string().trim().max(20, "20 caractères maximum"),
  rc: z.string().trim().max(30, "30 caractères maximum"),
  contact: z.string().trim().max(120, "120 caractères maximum"),
  adresse: z.string().trim().max(300, "300 caractères maximum"),
  email: z.union([z.literal(""), z.email("Adresse email invalide").max(120, "120 caractères maximum")]),
});
type Champs = z.infer<typeof schema>;

/** Champ facultatif du formulaire, envoyé absent s'il est vide. */
function ouAbsent(valeur: string): string | undefined {
  return valeur.trim() || undefined;
}

/** Création ou modification d'un client (réactivation comprise) ; la suppression part d'ici. */
function ClientDialogue({
  client,
  onFermer,
  onSupprimer,
}: {
  client: Client | null;
  onFermer: () => void;
  onSupprimer?: () => void;
}) {
  const enregistrer = useEnregistrerClient();
  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors },
  } = useForm<Champs>({
    resolver: zodResolver(schema),
    defaultValues: {
      type: client?.type ?? "PARTICULIER",
      nom: client?.nom ?? "",
      telephone: client?.telephone ?? "",
      note: client?.note ?? "",
      plafond: client?.plafond != null ? decimal(client.plafond).replace(/\s/g, "") : "",
      actif: client?.actif ?? true,
      ice: client?.ice ?? "",
      identifiantFiscal: client?.identifiantFiscal ?? "",
      rc: client?.rc ?? "",
      contact: client?.contact ?? "",
      adresse: client?.adresse ?? "",
      email: client?.email ?? "",
    },
  });
  const entreprise = useWatch({ control, name: "type" }) === "ENTREPRISE";

  const envoyer = handleSubmit((champs) =>
    enregistrer.mutate(
      {
        id: client?.id ?? null,
        type: champs.type,
        nom: champs.nom,
        telephone: ouAbsent(champs.telephone),
        note: ouAbsent(champs.note),
        plafond: champs.plafond ? lireMontant(champs.plafond) : null,
        actif: champs.actif,
        ice: ouAbsent(champs.ice),
        identifiantFiscal: ouAbsent(champs.identifiantFiscal),
        rc: ouAbsent(champs.rc),
        contact: ouAbsent(champs.contact),
        adresse: ouAbsent(champs.adresse),
        email: ouAbsent(champs.email),
      },
      { onSuccess: onFermer },
    ),
  );

  const champ = (
    nom: keyof Champs,
    libelle: string,
    options: {
      placeholder?: string;
      inputMode?: "tel" | "decimal" | "numeric" | "email";
      large?: boolean;
    } = {},
  ) => (
    <label className={`champ${options.large ? " champ-large" : ""}`}>
      <span className="libelle">{libelle}</span>
      <input
        placeholder={options.placeholder}
        inputMode={options.inputMode}
        autoComplete="off"
        {...register(nom)}
      />
      {errors[nom] && <span className="erreur-champ">{errors[nom].message}</span>}
    </label>
  );

  return (
    <Dialogue
      titre={client ? `Modifier ${client.nom}` : "Nouveau client"}
      onFermer={onFermer}
      largeur={entreprise ? 600 : undefined}
    >
      <form className="formulaire" onSubmit={envoyer} noValidate>
        <div className="choix-type" role="group" aria-label="Type de client">
          {(
            [
              ["PARTICULIER", "Particulier"],
              ["ENTREPRISE", "Entreprise"],
            ] as const
          ).map(([valeur, libelle]) => (
            <button
              key={valeur}
              type="button"
              aria-pressed={(valeur === "ENTREPRISE") === entreprise}
              onClick={() => setValue("type", valeur, { shouldDirty: true })}
            >
              {libelle}
            </button>
          ))}
        </div>
        <label className="champ">
          <span className="libelle">{entreprise ? "Raison sociale" : "Nom"}</span>
          <input
            autoFocus
            autoComplete="off"
            placeholder={entreprise ? "Ex. Épicerie Atlas SARL" : "Nom et prénom"}
            {...register("nom")}
          />
          {errors.nom && <span className="erreur-champ">{errors.nom.message}</span>}
        </label>
        {entreprise ? (
          <div className="grille-deux-colonnes">
            {champ("ice", "ICE (facultatif)", { placeholder: "15 chiffres", inputMode: "numeric" })}
            {champ("telephone", "Téléphone (facultatif)", {
              placeholder: "05 22 00 00 00",
              inputMode: "tel",
            })}
            {champ("identifiantFiscal", "IF (facultatif)", { placeholder: "Identifiant fiscal" })}
            {champ("rc", "RC (facultatif)", { placeholder: "Registre de commerce" })}
            {champ("contact", "Personne à contacter (facultatif)")}
            {champ("email", "Email (facultatif)", { placeholder: "contact@exemple.ma", inputMode: "email" })}
            {champ("adresse", "Adresse (facultatif)", { large: true })}
          </div>
        ) : (
          champ("telephone", "Téléphone (facultatif)", { placeholder: "06 00 00 00 00", inputMode: "tel" })
        )}
        {champ("note", "Note (facultatif)", { placeholder: "Ex. paie en fin de mois" })}
        <label className="champ">
          <span className="libelle">Plafond de crédit (facultatif)</span>
          <input inputMode="decimal" placeholder="Sans limite" {...register("plafond")} />
          <span className="texte-secondaire">
            Une vente à crédit qui porterait sa dette au-delà sera refusée.
          </span>
          {errors.plafond && <span className="erreur-champ">{errors.plafond.message}</span>}
        </label>
        {entreprise && (
          <p className="texte-secondaire">
            Seule la raison sociale est obligatoire. L'ICE, l'IF, le RC et l'adresse figurent sur la facture
            A4 quand ils sont renseignés.
          </p>
        )}
        {client && (
          <label className="case">
            <input type="checkbox" {...register("actif")} />
            Actif (proposé à la caisse)
          </label>
        )}
        {enregistrer.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(enregistrer.error)}
          </p>
        )}
        <div className="rangee-boutons">
          {onSupprimer && client?.actif && (
            <button type="button" className="bouton bouton-danger rangee-gauche" onClick={onSupprimer}>
              Supprimer
            </button>
          )}
          <button type="button" className="bouton bouton-contour" onClick={onFermer}>
            Annuler
          </button>
          <button type="submit" className="bouton bouton-plein" disabled={enregistrer.isPending}>
            {client ? "Enregistrer" : "Créer le client"}
          </button>
        </div>
      </form>
    </Dialogue>
  );
}
