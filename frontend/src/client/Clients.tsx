import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
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
  const [filtre, setFiltre] = useState<"tous" | "dette" | "desactives">("tous");
  const clients = useClients(recherche, true);
  const [popup, setPopup] = useState<Popup | null>(null);

  const tous = clients.data ?? [];
  const actifs = tous.filter((c) => c.actif);
  const totalDettes = tous.filter((c) => c.dette > 0).reduce((somme, c) => somme + c.dette, 0);
  const liste = tous.filter((c) =>
    filtre === "dette" ? c.dette > 0 : filtre === "desactives" ? !c.actif : c.actif,
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
          placeholder="Rechercher…"
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

const schema = z.object({
  nom: z.string().trim().min(1, "Nom obligatoire").max(120, "120 caractères maximum"),
  telephone: z.string().trim().max(30, "30 caractères maximum"),
  note: z.string().trim().max(300, "300 caractères maximum"),
  plafond: z
    .string()
    .trim()
    .refine((v) => v === "" || (lireMontant(v) ?? 0) > 0, "Montant invalide (supérieur à 0)"),
  actif: z.boolean(),
});
type Champs = z.infer<typeof schema>;

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
    formState: { errors },
  } = useForm<Champs>({
    resolver: zodResolver(schema),
    defaultValues: {
      nom: client?.nom ?? "",
      telephone: client?.telephone ?? "",
      note: client?.note ?? "",
      plafond: client?.plafond != null ? decimal(client.plafond).replace(/\s/g, "") : "",
      actif: client?.actif ?? true,
    },
  });

  const envoyer = handleSubmit((champs) =>
    enregistrer.mutate(
      {
        id: client?.id ?? null,
        nom: champs.nom,
        telephone: champs.telephone || undefined,
        note: champs.note || undefined,
        plafond: champs.plafond ? lireMontant(champs.plafond) : null,
        actif: champs.actif,
      },
      { onSuccess: onFermer },
    ),
  );

  return (
    <Dialogue titre={client ? `Modifier ${client.nom}` : "Nouveau client"} onFermer={onFermer}>
      <form className="formulaire" onSubmit={envoyer} noValidate>
        <label className="champ">
          <span className="libelle">Nom</span>
          <input autoFocus placeholder="Nom et prénom, ou raison sociale" {...register("nom")} />
          {errors.nom && <span className="erreur-champ">{errors.nom.message}</span>}
        </label>
        <label className="champ">
          <span className="libelle">Téléphone (facultatif)</span>
          <input inputMode="tel" placeholder="06 00 00 00 00" {...register("telephone")} />
          {errors.telephone && <span className="erreur-champ">{errors.telephone.message}</span>}
        </label>
        <label className="champ">
          <span className="libelle">Note (facultatif)</span>
          <input placeholder="Ex. paie en fin de mois" {...register("note")} />
          {errors.note && <span className="erreur-champ">{errors.note.message}</span>}
        </label>
        <label className="champ">
          <span className="libelle">Plafond de crédit (facultatif)</span>
          <input inputMode="decimal" placeholder="Sans limite" {...register("plafond")} />
          <span className="texte-secondaire">
            Une vente à crédit qui porterait sa dette au-delà sera refusée.
          </span>
          {errors.plafond && <span className="erreur-champ">{errors.plafond.message}</span>}
        </label>
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
