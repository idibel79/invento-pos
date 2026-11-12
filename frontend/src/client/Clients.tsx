import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { messageErreur } from "../api/erreurs";
import { useUtilisateur } from "../auth/AuthContext";
import { Dialogue } from "../ui/Dialogue";
import { decimal, dh, lireMontant } from "../ui/format";
import { EncaisserDetteDialogue } from "./EncaisserDetteDialogue";
import { ReleveDialogue } from "./Releve";
import { VentesClientDialogue } from "./VentesClient";
import { type Client, texteSolde, useClients, useEnregistrerClient, useSupprimerClient } from "./requetes";

type Popup =
  | { type: "nouveau" }
  | { type: "modifier"; client: Client }
  | { type: "releve"; client: Client }
  | { type: "fiche"; client: Client }
  | { type: "ventes"; client: Client }
  | { type: "dette"; client: Client }
  | { type: "supprimer"; client: Client };

const FILTRES = [
  ["tous", "Tous"],
  ["entreprises", "Entreprises"],
  ["desactives", "Désactivés"],
] as const;
type Filtre = (typeof FILTRES)[number][0];

/**
 * Administration › Clients, design B, en trois vues de la même liste :
 * - « annuaire » : un clic ouvre la fiche du client ; l'admin crée, modifie et supprime ;
 * - « ventes » : un clic ou le bouton Ventes ouvre ses ventes, pour réimprimer un ticket ou une facture ;
 * - « dettes » : seuls les clients qui doivent de l'argent ou ont une avance, du plus endetté au moins
 *   endetté, avec leur dette et Encaisser ; un clic ouvre le relevé.
 * Seuls les clients de l'annuaire peuvent payer à crédit.
 */
export function Clients({ vue = "annuaire" }: { vue?: "annuaire" | "ventes" | "dettes" }) {
  const admin = useUtilisateur().role === "ADMIN";
  const dettes = vue === "dettes";
  const ouvrir = (client: Client) =>
    setPopup({ type: vue === "dettes" ? "releve" : vue === "ventes" ? "ventes" : "fiche", client });
  const [recherche, setRecherche] = useState("");
  const [filtre, setFiltre] = useState<Filtre>("tous");
  const clients = useClients(recherche, admin);
  const [popup, setPopup] = useState<Popup | null>(null);

  const tous = clients.data ?? [];
  const actifs = tous.filter((c) => c.actif);
  const totalDettes = tous.filter((c) => c.dette > 0).reduce((somme, c) => somme + c.dette, 0);
  const liste = dettes
    ? tous.filter((c) => Math.abs(c.dette) > 0.005).sort((a, b) => b.dette - a.dette)
    : tous.filter((c) =>
        filtre === "desactives"
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
          {dettes ? "Crédits et dettes" : vue === "ventes" ? "Ventes et factures" : "Clients"}{" "}
          <span className="texte-secondaire clients-resume">
            {dettes
              ? `· ${liste.filter((c) => c.dette > 0).length} clients doivent ${dh(totalDettes)}`
              : `· ${actifs.length} actifs`}
          </span>
        </h1>
        {!dettes && (
          <div className="clients-filtres" role="group" aria-label="Filtre">
            {FILTRES.filter(([valeur]) => admin || valeur !== "desactives").map(([valeur, libelle]) => (
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
        )}
        <input
          type="search"
          className="clients-recherche"
          aria-label="Rechercher un client"
          placeholder="Nom, téléphone ou ICE…"
          value={recherche}
          onChange={(e) => setRecherche(e.target.value)}
        />
        {admin && vue === "annuaire" && (
          <button type="button" className="bouton bouton-plein" onClick={() => setPopup({ type: "nouveau" })}>
            + Nouveau client
          </button>
        )}
      </div>
      {clients.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(clients.error)}
        </p>
      )}
      <div
        className={`clients-tableau${dettes ? "" : " clients-annuaire"}`}
        role="table"
        aria-label="Clients"
      >
        <div className="clients-ligne clients-entete" role="row">
          <span role="columnheader">Nom</span>
          {!dettes && <span role="columnheader">Type</span>}
          <span role="columnheader">Téléphone</span>
          <span role="columnheader">Note</span>
          {dettes && (
            <span role="columnheader" className="a-droite">
              Dette
            </span>
          )}
          <span role="columnheader">
            <span className="visuellement-cache">Actions</span>
          </span>
        </div>
        {clients.isPending && <p className="vide">Chargement…</p>}
        {clients.isSuccess && liste.length === 0 && (
          <p className="vide">
            {recherche.trim() || filtre !== "tous"
              ? "Aucun client ne correspond."
              : dettes
                ? "Aucun client ne doit d'argent."
                : admin
                  ? "Aucun client. Ajoutez le premier."
                  : "Aucun client. Seul l'admin en ajoute."}
          </p>
        )}
        {liste.map((c) => (
          <div
            key={c.id}
            role="row"
            className={`clients-ligne cliquable${c.actif ? "" : " inactive"}`}
            title={
              dettes ? "Voir le relevé" : vue === "ventes" ? "Voir ses ventes" : "Voir la fiche du client"
            }
            onClick={() => ouvrir(c)}
          >
            <span role="cell" className="clients-nom">
              <button
                type="button"
                className="lien-nom"
                onClick={(e) => {
                  e.stopPropagation();
                  ouvrir(c);
                }}
              >
                {c.nom}
              </button>
              {!c.actif && <span className="statut statut-gris">Désactivé</span>}
            </span>
            {!dettes && (
              <span role="cell" className="texte-secondaire">
                {c.type === "ENTREPRISE" ? "Entreprise" : "Particulier"}
              </span>
            )}
            <span role="cell" className="texte-secondaire">
              {c.telephone ?? "—"}
            </span>
            <span role="cell" className="texte-secondaire clients-note">
              {c.note ?? ""}
            </span>
            {dettes && (
              <span
                role="cell"
                className={`a-droite chiffres${c.dette > 0 ? " texte-dette" : " texte-secondaire"}`}
              >
                {texteSolde(c.dette)}
              </span>
            )}
            <span role="cell" className="clients-actions" onClick={(e) => e.stopPropagation()}>
              {dettes && (
                <button
                  type="button"
                  className="bouton bouton-plein bouton-petit"
                  onClick={() => setPopup({ type: "dette", client: c })}
                >
                  Encaisser
                </button>
              )}
              {vue === "ventes" && (
                <button
                  type="button"
                  className="bouton bouton-contour bouton-petit"
                  onClick={() => setPopup({ type: "ventes", client: c })}
                >
                  Ventes
                </button>
              )}
              {admin && vue === "annuaire" && (
                <button
                  type="button"
                  className="bouton bouton-contour bouton-petit"
                  onClick={() => setPopup({ type: "modifier", client: c })}
                >
                  Modifier
                </button>
              )}
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
      {popup?.type === "fiche" && (
        <FicheClient
          client={popup.client}
          onFermer={fermer}
          onModifier={admin ? () => setPopup({ type: "modifier", client: popup.client }) : undefined}
        />
      )}
      {popup?.type === "releve" && (
        <ReleveDialogue client={popup.client} onFermer={fermer} annulable={admin} />
      )}
      {popup?.type === "ventes" && <VentesClientDialogue client={popup.client} onFermer={fermer} />}
      {popup?.type === "dette" && <EncaisserDetteDialogue clientInitial={popup.client} onFermer={fermer} />}
      {popup?.type === "supprimer" && <SuppressionDialogue client={popup.client} onFermer={fermer} />}
    </section>
  );
}

/**
 * Fiche d'un client de l'annuaire : toutes ses informations, sans son relevé (vue Crédits et dettes).
 * Les champs vides sont affichés « — » ; `onModifier` (admin) ouvre la modification.
 */
function FicheClient({
  client: c,
  onFermer,
  onModifier,
}: {
  client: Client;
  onFermer: () => void;
  onModifier?: () => void;
}) {
  const entreprise = c.type === "ENTREPRISE";
  const champs: [string, string | null | undefined][] = [
    ["Type", entreprise ? "Entreprise" : "Particulier"],
    [entreprise ? "Raison sociale" : "Nom et prénom", c.nom],
    ["Téléphone", c.telephone],
    ...(entreprise
      ? ([
          ["Email", c.email],
          ["Personne à contacter", c.contact],
          ["Adresse", c.adresse],
          ["ICE", c.ice],
          ["IF", c.identifiantFiscal],
          ["RC", c.rc],
        ] as [string, string | null | undefined][])
      : []),
    ["Note", c.note],
    ["Plafond de crédit", c.plafond != null ? dh(c.plafond) : "Sans limite"],
    ["Statut", c.actif ? "Actif (proposé à la caisse)" : "Désactivé"],
  ];

  return (
    <Dialogue titre={c.nom} sousTitre="Fiche du client" onFermer={onFermer} largeur={520}>
      <div className="formulaire">
        <dl className="fiche-client">
          {champs.map(([libelle, valeur]) => (
            <div key={libelle} className="fiche-client-ligne">
              <dt>{libelle}</dt>
              <dd className={valeur?.trim() ? undefined : "texte-secondaire"}>{valeur?.trim() || "—"}</dd>
            </div>
          ))}
        </dl>
        <div className="rangee-boutons">
          <button
            type="button"
            className={onModifier ? "bouton bouton-contour" : "bouton bouton-plein"}
            onClick={onFermer}
          >
            Fermer
          </button>
          {onModifier && (
            <button type="button" className="bouton bouton-plein" onClick={onModifier}>
              Modifier
            </button>
          )}
        </div>
      </div>
    </Dialogue>
  );
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
