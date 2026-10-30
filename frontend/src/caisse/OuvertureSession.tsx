import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { messageErreur } from "../api/erreurs";
import { useAuth, useUtilisateur } from "../auth/AuthContext";
import { lireMontant } from "../ui/format";
import { Logo } from "../ui/Logo";
import { useCaisses, useOuvrirSession } from "./requetes";

const schema = z.object({
  caisseId: z.string().min(1, "Choisissez une caisse"),
  fondInitial: z.string().refine((v) => lireMontant(v) !== null, "Montant invalide"),
});
type Champs = z.infer<typeof schema>;

/** Ouverture de la caisse avec le fond de caisse (espèces présentes au départ). */
export function OuvertureSession({ onAdministration }: { onAdministration?: () => void }) {
  const utilisateur = useUtilisateur();
  const { deconnecter } = useAuth();
  const caisses = useCaisses();
  const ouvrir = useOuvrirSession();
  const autorisees = (caisses.data ?? []).filter(
    (c) => c.active && (utilisateur.role === "ADMIN" || utilisateur.caisseIds.includes(c.id)),
  );
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Champs>({
    resolver: zodResolver(schema),
    values: { caisseId: autorisees.length === 1 ? String(autorisees[0]?.id) : "", fondInitial: "" },
  });

  const envoyer = handleSubmit(({ caisseId, fondInitial }) =>
    ouvrir.mutate({ caisseId: Number(caisseId), fondInitial: lireMontant(fondInitial) ?? 0 }),
  );

  return (
    <main className="page-centree">
      <form className="carte carte-formulaire" onSubmit={envoyer} noValidate>
        <Logo />
        <h1 className="titre-popup">Ouvrir la caisse</h1>
        <p className="texte-secondaire">
          Bonjour {utilisateur.prenom}. Comptez les espèces présentes dans le tiroir avant de commencer.
        </p>
        {caisses.isSuccess && autorisees.length === 0 ? (
          <p className="alerte alerte-danger" role="alert">
            {onAdministration
              ? "Aucune caisse active. Créez-en une dans l'administration."
              : "Aucune caisse ne vous est attribuée. Demandez à l'administrateur de vous en affecter une."}
          </p>
        ) : (
          <>
            <div className="champ">
              <label className="libelle" htmlFor="caisse">
                Caisse
              </label>
              <select id="caisse" {...register("caisseId")} disabled={caisses.isPending}>
                <option value="">Choisir…</option>
                {autorisees.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.nom}
                  </option>
                ))}
              </select>
              {errors.caisseId && <span className="erreur-champ">{errors.caisseId.message}</span>}
            </div>
            <label className="champ">
              <span className="libelle">Fond de caisse (DH)</span>
              <input inputMode="decimal" placeholder="0,00" autoFocus {...register("fondInitial")} />
              {errors.fondInitial && <span className="erreur-champ">{errors.fondInitial.message}</span>}
            </label>
          </>
        )}
        {ouvrir.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(ouvrir.error)}
          </p>
        )}
        <div className="rangee-boutons">
          <button type="button" className="bouton bouton-contour" onClick={() => void deconnecter()}>
            Se déconnecter
          </button>
          {onAdministration && (
            <button type="button" className="bouton bouton-contour" onClick={onAdministration}>
              Administration
            </button>
          )}
          <button
            type="submit"
            className="bouton bouton-plein"
            disabled={ouvrir.isPending || autorisees.length === 0}
          >
            Ouvrir la caisse
          </button>
        </div>
      </form>
    </main>
  );
}
