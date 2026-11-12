import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useParametres } from "../caisse/requetes";
import { TicketCaisse } from "../vente/TicketCaisse";
import { useModifierParametres } from "./requetes";

// Règles indicatives pour guider la saisie ; le serveur reste seul juge.
const schema = z.object({
  boutiqueNom: z.string().trim().max(120, "120 caractères maximum"),
  boutiqueAdresse: z.string().trim().max(300, "300 caractères maximum"),
  boutiqueTelephone: z
    .string()
    .trim()
    .max(40, "40 caractères maximum")
    .regex(/^[0-9+ ().-]*$/, "Chiffres, espaces, +, -, . et parenthèses uniquement"),
  boutiqueEmail: z.union([
    z.literal(""),
    z.email("Adresse email invalide").max(120, "120 caractères maximum"),
  ]),
  boutiqueIce: z
    .string()
    .trim()
    .refine((v) => v === "" || /^\d{15}$/.test(v.replace(/\s/g, "")), "L'ICE compte 15 chiffres"),
  boutiqueIf: z.string().trim().max(20, "20 caractères maximum"),
  boutiqueRc: z.string().trim().max(30, "30 caractères maximum"),
  boutiquePatente: z.string().trim().max(20, "20 caractères maximum"),
  ticketMessage: z.string().trim().max(300, "300 caractères maximum"),
});

type Champs = z.infer<typeof schema>;

/** Vente fictive pour l'aperçu du ticket : seul l'en-tête et le pied dépendent de ce formulaire. */
const VENTE_EXEMPLE: Schemas["VenteDto"] = {
  id: 0,
  numero: "000123",
  statut: "VALIDEE",
  caisseId: 0,
  caissierId: 0,
  lignes: [
    {
      produitId: 1,
      designation: "Article exemple",
      quantite: 2,
      prixUnitaire: 25,
      tauxTva: 20,
      remise: null,
      montantBrut: 50,
      remiseMontant: 0,
      montantNet: 50,
      stockSuffisant: true,
    },
  ],
  totaux: {
    sousTotal: 50,
    remisesLignes: 0,
    remiseTicket: 0,
    totalRemises: 0,
    totalHt: 41.67,
    totalTva: 8.33,
    totalTtc: 50,
    tva: [{ taux: 20, totalHt: 41.67, totalTva: 8.33, totalTtc: 50 }],
  },
  remiseTicket: null,
  motifRemise: null,
  valideePar: null,
  noteAttente: null,
  attenteExpireLe: null,
  creeLe: new Date().toISOString(),
  finaliseeLe: null,
  paiement: { mode: "ESPECES", montant: 50, montantRecu: 100, rendu: 50 },
  paiements: [{ mode: "ESPECES", montant: 50, montantRecu: 100, rendu: 50 }],
  client: null,
};

/** Paramètres > Boutique : informations imprimées en tête et en pied du ticket de caisse. */
export function Boutique() {
  const parametres = useParametres();

  return (
    <section className="admin-section">
      <div className="admin-tete">
        <div>
          <h1 className="admin-titre">Boutique</h1>
          <p className="texte-secondaire">
            Ces informations sont imprimées sur le ticket de caisse remis au client. Les champs vides ne sont
            pas imprimés.
          </p>
        </div>
      </div>
      {parametres.isPending && <p className="vide">Chargement…</p>}
      {parametres.isError && (
        <div className="alerte alerte-danger" role="alert">
          {messageErreur(parametres.error)}{" "}
          <button type="button" className="bouton-petit danger" onClick={() => void parametres.refetch()}>
            Réessayer
          </button>
        </div>
      )}
      {parametres.data && <FormulaireBoutique parametres={parametres.data} />}
    </section>
  );
}

function FormulaireBoutique({ parametres }: { parametres: Schemas["Parametres"] }) {
  const modifier = useModifierParametres();
  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors, isDirty },
  } = useForm<Champs>({
    resolver: zodResolver(schema),
    defaultValues: {
      boutiqueNom: parametres.boutiqueNom,
      boutiqueAdresse: parametres.boutiqueAdresse,
      boutiqueTelephone: parametres.boutiqueTelephone,
      boutiqueEmail: parametres.boutiqueEmail,
      boutiqueIce: parametres.boutiqueIce,
      boutiqueIf: parametres.boutiqueIf,
      boutiqueRc: parametres.boutiqueRc,
      boutiquePatente: parametres.boutiquePatente,
      ticketMessage: parametres.ticketMessage,
    },
  });
  const saisie = useWatch({ control });

  const envoyer = handleSubmit((c) =>
    modifier.mutate(
      { ...c, fuseauHoraire: parametres.fuseauHoraire },
      {
        onSuccess: (p) =>
          reset({
            boutiqueNom: p.boutiqueNom,
            boutiqueAdresse: p.boutiqueAdresse,
            boutiqueTelephone: p.boutiqueTelephone,
            boutiqueEmail: p.boutiqueEmail,
            boutiqueIce: p.boutiqueIce,
            boutiqueIf: p.boutiqueIf,
            boutiqueRc: p.boutiqueRc,
            boutiquePatente: p.boutiquePatente,
            ticketMessage: p.ticketMessage,
          }),
      },
    ),
  );

  return (
    <div className="boutique-ecran">
      <form className="carte admin-carte formulaire" onSubmit={envoyer} noValidate>
        <label className="champ">
          <span className="libelle">Nom de la boutique</span>
          <input autoComplete="off" {...register("boutiqueNom")} />
          {errors.boutiqueNom && <span className="erreur-champ">{errors.boutiqueNom.message}</span>}
        </label>
        <label className="champ">
          <span className="libelle">Adresse</span>
          <textarea rows={2} {...register("boutiqueAdresse")} />
          {errors.boutiqueAdresse && <span className="erreur-champ">{errors.boutiqueAdresse.message}</span>}
        </label>
        <div className="deux-colonnes">
          <label className="champ">
            <span className="libelle">Téléphone</span>
            <input inputMode="tel" autoComplete="off" {...register("boutiqueTelephone")} />
            {errors.boutiqueTelephone && (
              <span className="erreur-champ">{errors.boutiqueTelephone.message}</span>
            )}
          </label>
          <label className="champ">
            <span className="libelle">Email</span>
            <input type="email" autoComplete="off" spellCheck={false} {...register("boutiqueEmail")} />
            {errors.boutiqueEmail && <span className="erreur-champ">{errors.boutiqueEmail.message}</span>}
          </label>
        </div>
        <fieldset className="boutique-legaux">
          <legend className="libelle">Identifiants légaux (facultatifs, imprimés sur la facture A4)</legend>
          <div className="grille-deux-colonnes">
            <label className="champ">
              <span className="libelle">ICE</span>
              <input
                inputMode="numeric"
                placeholder="15 chiffres"
                autoComplete="off"
                {...register("boutiqueIce")}
              />
              {errors.boutiqueIce && <span className="erreur-champ">{errors.boutiqueIce.message}</span>}
            </label>
            <label className="champ">
              <span className="libelle">IF (identifiant fiscal)</span>
              <input autoComplete="off" {...register("boutiqueIf")} />
              {errors.boutiqueIf && <span className="erreur-champ">{errors.boutiqueIf.message}</span>}
            </label>
            <label className="champ">
              <span className="libelle">RC (registre de commerce)</span>
              <input autoComplete="off" {...register("boutiqueRc")} />
              {errors.boutiqueRc && <span className="erreur-champ">{errors.boutiqueRc.message}</span>}
            </label>
            <label className="champ">
              <span className="libelle">Patente</span>
              <input autoComplete="off" {...register("boutiquePatente")} />
              {errors.boutiquePatente && (
                <span className="erreur-champ">{errors.boutiquePatente.message}</span>
              )}
            </label>
          </div>
        </fieldset>
        <label className="champ">
          <span className="libelle">Message en bas du ticket</span>
          <textarea rows={2} placeholder="Merci de votre visite !" {...register("ticketMessage")} />
          {errors.ticketMessage && <span className="erreur-champ">{errors.ticketMessage.message}</span>}
        </label>
        {modifier.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(modifier.error)}
          </p>
        )}
        {modifier.isSuccess && !isDirty && (
          <p className="alerte alerte-succes" role="status">
            Informations enregistrées.
          </p>
        )}
        <div className="rangee-boutons">
          <button type="submit" className="bouton bouton-plein" disabled={!isDirty || modifier.isPending}>
            {modifier.isPending ? "Enregistrement…" : "Enregistrer"}
          </button>
        </div>
      </form>
      <aside className="boutique-apercu" aria-label="Aperçu du ticket">
        <span className="libelle">Aperçu du ticket</span>
        <TicketCaisse
          vente={VENTE_EXEMPLE}
          boutique={{
            boutiqueNom: saisie.boutiqueNom ?? "",
            boutiqueAdresse: saisie.boutiqueAdresse ?? "",
            boutiqueTelephone: saisie.boutiqueTelephone ?? "",
            boutiqueEmail: saisie.boutiqueEmail ?? "",
            ticketMessage: saisie.ticketMessage ?? "",
          }}
          vendeur="Prénom Nom"
          fuseau={parametres.fuseauHoraire}
        />
      </aside>
    </div>
  );
}
