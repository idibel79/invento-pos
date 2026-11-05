import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import {
  nouvelleCle,
  useAjuster,
  useAlimenter,
  useRetirer,
  useVerserAuCaissier,
} from "../tresorerie/requetes";
import { Dialogue } from "../ui/Dialogue";
import { dh, lireMontant } from "../ui/format";

export type TypeOperation = "alimenter" | "retirer" | "verser" | "ajuster";

const TEXTES: Record<
  TypeOperation,
  { titre: string; bouton: string; motifObligatoire: boolean; aide: string }
> = {
  alimenter: {
    titre: "Alimenter la caisse principale",
    bouton: "Alimenter",
    motifObligatoire: false,
    aide: "Argent ajouté à la caisse principale (apport, fonds de départ…).",
  },
  retirer: {
    titre: "Retirer de la caisse principale",
    bouton: "Retirer",
    motifObligatoire: true,
    aide: "Argent sorti de la caisse principale, par exemple un dépôt en banque.",
  },
  verser: {
    titre: "Verser à un caissier",
    bouton: "Verser",
    motifObligatoire: false,
    aide: "Débité de la caisse principale tout de suite, crédité au caissier quand il confirme l'avoir reçu.",
  },
  ajuster: {
    titre: "Ajuster le solde (passage en perte)",
    bouton: "Passer en perte",
    motifObligatoire: true,
    aide: "Réduit ce que le caissier doit, sans effet sur la caisse principale.",
  },
};

interface Props {
  type: TypeOperation;
  soldePrincipale: number;
  caissiers: Schemas["CompteCaissierDto"][];
  /** Caissier présélectionné (versement, ajustement). */
  utilisateurId?: number;
  onFermer: () => void;
}

/** Alimentation, retrait, versement à un caissier ou ajustement : montant, caissier et motif. */
export function OperationTresorerieDialogue({
  type,
  soldePrincipale,
  caissiers,
  utilisateurId,
  onFermer,
}: Props) {
  const textes = TEXTES[type];
  const avecCaissier = type === "verser" || type === "ajuster";
  const schema = z.object({
    utilisateurId: avecCaissier ? z.string().min(1, "Choisissez un caissier") : z.string(),
    montant: z.string().refine((v) => (lireMontant(v) ?? 0) > 0, "Montant invalide"),
    motif: textes.motifObligatoire
      ? z.string().trim().min(1, "Le motif est obligatoire").max(300)
      : z.string().trim().max(300),
  });
  type Champs = z.infer<typeof schema>;
  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<Champs>({
    resolver: zodResolver(schema),
    defaultValues: { utilisateurId: utilisateurId ? String(utilisateurId) : "", montant: "", motif: "" },
  });
  const [cle] = useState(nouvelleCle);
  const alimenter = useAlimenter();
  const retirer = useRetirer();
  const verser = useVerserAuCaissier();
  const ajuster = useAjuster();
  const mutation = { alimenter, retirer, verser, ajuster }[type];
  const utilisateurChoisi = useWatch({ control, name: "utilisateurId" });
  const choisi = caissiers.find((c) => String(c.utilisateurId) === utilisateurChoisi);

  const envoyer = handleSubmit((champs) => {
    const corps = { montant: lireMontant(champs.montant) ?? 0, motif: champs.motif || undefined, cle };
    const options = { onSuccess: onFermer };
    if (type === "alimenter") alimenter.mutate(corps, options);
    if (type === "retirer") retirer.mutate(corps, options);
    if (type === "verser") verser.mutate({ ...corps, utilisateurId: Number(champs.utilisateurId) }, options);
    if (type === "ajuster")
      ajuster.mutate({ ...corps, utilisateurId: Number(champs.utilisateurId) }, options);
  });

  return (
    <Dialogue titre={textes.titre} onFermer={onFermer} largeur={460}>
      <form className="formulaire" onSubmit={envoyer} noValidate>
        <p className="texte-secondaire">{textes.aide}</p>
        {avecCaissier && (
          <label className="champ">
            <span className="libelle">Caissier</span>
            <select {...register("utilisateurId")} disabled={utilisateurId !== undefined}>
              <option value="">Choisir…</option>
              {caissiers
                .filter((c) => c.actif || c.utilisateurId === utilisateurId)
                .map((c) => (
                  <option key={c.utilisateurId} value={c.utilisateurId}>
                    {c.nom} · {dh(c.solde)}
                  </option>
                ))}
            </select>
            {errors.utilisateurId && <span className="erreur-champ">{errors.utilisateurId.message}</span>}
          </label>
        )}
        <label className="champ">
          <span className="libelle">Montant (DH)</span>
          <input
            inputMode="decimal"
            placeholder="0,00"
            autoComplete="off"
            autoFocus
            {...register("montant")}
          />
          {errors.montant && <span className="erreur-champ">{errors.montant.message}</span>}
          <span className="texte-secondaire">
            {type === "ajuster"
              ? `Solde du caissier : ${choisi ? dh(choisi.solde) : "—"}`
              : `Caisse principale : ${dh(soldePrincipale)}`}
          </span>
        </label>
        <label className="champ">
          <span className="libelle">Motif{textes.motifObligatoire ? "" : " (facultatif)"}</span>
          <input maxLength={300} autoComplete="off" {...register("motif")} />
          {errors.motif && <span className="erreur-champ">{errors.motif.message}</span>}
        </label>
        {mutation.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(mutation.error)}
          </p>
        )}
        <div className="rangee-boutons">
          <button type="button" className="bouton bouton-contour" onClick={onFermer}>
            Annuler
          </button>
          <button
            type="submit"
            className={`bouton bouton-plein${type === "retirer" || type === "ajuster" ? " danger" : ""}`}
            disabled={mutation.isPending}
          >
            {textes.bouton}
          </button>
        </div>
      </form>
    </Dialogue>
  );
}
