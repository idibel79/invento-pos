import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { messageErreur } from "../api/erreurs";
import { useAuth, useUtilisateur } from "../auth/AuthContext";
import { lireMontant } from "../ui/format";
import { Logo } from "../ui/Logo";
import { BoutonMonCompte } from "../tresorerie/MonComptePanneau";
import { useMonCompte } from "../tresorerie/requetes";
import { dh } from "../ui/format";
import { useCaisses, useOuvrirSession } from "./requetes";

const schema = z.object({
  caisseId: z.string().min(1, "Choisissez une caisse"),
  fondInitial: z.string().refine((v) => lireMontant(v) !== null, "Montant invalide"),
});
type Champs = z.infer<typeof schema>;

interface Props {
  /** Raison du retour à cet écran (session clôturée d'office), s'il y en a une. */
  avis?: string | null;
  onAdministration?: () => void;
}

/** Ouverture de la caisse avec le fond de caisse, pris sur le solde du compte du caissier. */
export function OuvertureSession({ avis, onAdministration }: Props) {
  const utilisateur = useUtilisateur();
  const { deconnecter } = useAuth();
  const caisses = useCaisses();
  const ouvrir = useOuvrirSession();
  const disponible = useMonCompte().data?.solde;
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
          Bonjour {utilisateur.prenom || utilisateur.nom}. Le tiroir est vide au départ : mettez-y le fond de
          caisse, pris sur l'argent de votre compte.
        </p>
        {avis && (
          <p className="alerte alerte-attention" role="alert">
            {avis}
          </p>
        )}
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
              <span className="libelle">Fond de caisse pris sur mon compte (DH)</span>
              <input inputMode="decimal" placeholder="0,00" autoFocus {...register("fondInitial")} />
              {errors.fondInitial && <span className="erreur-champ">{errors.fondInitial.message}</span>}
              <span className="texte-secondaire">
                Disponible : {disponible === undefined ? "…" : dh(disponible)}
              </span>
            </label>
          </>
        )}
        {ouvrir.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(ouvrir.error)}
          </p>
        )}
        <button
          type="submit"
          className="bouton bouton-plein ouverture-principal"
          disabled={ouvrir.isPending || autorisees.length === 0}
        >
          Ouvrir la caisse
        </button>
        <div className="ouverture-secondaires">
          <BoutonMonCompte classe="bouton bouton-contour" page />
          {onAdministration && (
            <button type="button" className="bouton bouton-contour" onClick={onAdministration}>
              Administration
            </button>
          )}
        </div>
        <button
          type="button"
          className="bouton-lien ouverture-deconnexion"
          onClick={() => void deconnecter()}
        >
          Se déconnecter
        </button>
      </form>
    </main>
  );
}
