import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useParametres } from "../caisse/requetes";
import { Dialogue } from "../ui/Dialogue";
import { dh, heure } from "../ui/format";
import { IconeAlerte, IconeCaisse, IconeCorbeille } from "../ui/icones";
import { EtatRequete } from "./EtatRequete";
import { useEnregistrerCaisse, useSuiviCaisses, useSupprimerCaisse, useUtilisateurs } from "./requetes";

type Caisse = Schemas["SuiviCaisseDto"];

/** Caisses du magasin et leur session en cours ; l'attribution aux utilisateurs se fait depuis leur fiche. */
export function Caisses() {
  const caisses = useSuiviCaisses();
  const utilisateurs = useUtilisateurs();
  const fuseau = useParametres().data?.fuseauHoraire;
  const basculer = useEnregistrerCaisse();
  const supprimer = useSupprimerCaisse();
  const [edition, setEdition] = useState<Caisse | "nouvelle" | null>(null);
  const [aDesactiver, setADesactiver] = useState<Caisse | null>(null);
  const [aSupprimer, setASupprimer] = useState<Caisse | null>(null);

  const nomUtilisateur = (id: number) => {
    const u = (utilisateurs.data ?? []).find((v) => v.id === id);
    return u ? `${u.prenom} ${u.nom}`.trim() : `#${id}`;
  };
  const attributaire = (caisse: Caisse) =>
    (utilisateurs.data ?? []).find((u) => u.role !== "ADMIN" && u.caisseIds.includes(caisse.id));

  const titulaireSupprime = aSupprimer && attributaire(aSupprimer);

  const changerEtat = (caisse: Caisse, active: boolean) =>
    basculer.mutate({ id: caisse.id, nom: caisse.nom, active }, { onSuccess: () => setADesactiver(null) });

  return (
    <section className="admin-section">
      <div className="admin-tete">
        <div>
          <h1 className="admin-titre">Caisses</h1>
          <p className="texte-secondaire">
            Une caisse inactive ne peut plus être ouverte. L'admin peut ouvrir toutes les caisses actives ;
            chaque autre utilisateur, la caisse qui lui est attribuée (onglet Utilisateurs).
          </p>
        </div>
        <button type="button" className="bouton bouton-plein" onClick={() => setEdition("nouvelle")}>
          Nouvelle caisse
        </button>
      </div>
      {basculer.isError && !aDesactiver && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(basculer.error)}
        </p>
      )}
      <EtatRequete requete={caisses} vide="Aucune caisse. Créez la première pour pouvoir ouvrir une session.">
        {(liste) => (
          <ul className="liste-admin">
            {liste.map((caisse) => {
              const titulaire = attributaire(caisse);
              const session = caisse.session;
              return (
                <li key={caisse.id} className={`ligne-admin${caisse.active ? "" : " inactive"}`}>
                  <div className="ligne-admin-texte">
                    <span className="ligne-admin-nom">
                      <button
                        type="button"
                        className="lien-nom"
                        title="Renommer la caisse"
                        onClick={() => setEdition(caisse)}
                      >
                        {caisse.nom}
                      </button>{" "}
                      {!caisse.active ? (
                        <span className="statut statut-gris">Inactive</span>
                      ) : session ? (
                        <span className="statut statut-ok">Ouverte</span>
                      ) : (
                        <span className="statut statut-info">Fermée</span>
                      )}
                    </span>
                    <span className="texte-secondaire">
                      {titulaire
                        ? `Attribuée à ${`${titulaire.prenom} ${titulaire.nom}`.trim()}`
                        : "Attribuée à aucun utilisateur"}
                      {session &&
                        ` · ouverte par ${nomUtilisateur(session.caissierId)} à ${heure(session.ouverture, fuseau)}`}
                    </span>
                  </div>
                  <dl className="caisse-especes">
                    <div>
                      <dt>Fond de caisse</dt>
                      <dd>{session ? dh(session.fondInitial) : "—"}</dd>
                    </div>
                    <div>
                      <dt>Espèces encaissées</dt>
                      <dd>{session ? dh(session.especesEncaissees) : "—"}</dd>
                    </div>
                    <div>
                      <dt>Espèces attendues</dt>
                      <dd className="caisse-especes-total">{session ? dh(session.especesAttendues) : "—"}</dd>
                    </div>
                  </dl>
                  <label className="interrupteur">
                    <input
                      type="checkbox"
                      role="switch"
                      checked={caisse.active}
                      disabled={basculer.isPending}
                      aria-label={`${caisse.nom} active`}
                      onChange={() => (caisse.active ? setADesactiver(caisse) : changerEtat(caisse, true))}
                    />
                    <span aria-hidden="true">{caisse.active ? "Active" : "Inactive"}</span>
                  </label>
                  {caisse.active ? (
                    <span className="bouton-icone-place" aria-hidden="true" />
                  ) : (
                    <button
                      type="button"
                      className="bouton-icone danger"
                      aria-label={`Supprimer ${caisse.nom}`}
                      title="Supprimer la caisse"
                      onClick={() => setASupprimer(caisse)}
                    >
                      <IconeCorbeille />
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </EtatRequete>
      {edition && (
        <CaisseDialogue caisse={edition === "nouvelle" ? null : edition} onFermer={() => setEdition(null)} />
      )}
      {aSupprimer && (
        <Dialogue
          titre={`Supprimer ${aSupprimer.nom} ?`}
          icone={<IconeCorbeille />}
          onFermer={() => {
            supprimer.reset();
            setASupprimer(null);
          }}
        >
          <div className="formulaire">
            <p className="alerte alerte-danger">
              Cette suppression est définitive : la caisse ne pourra plus être réactivée.
            </p>
            <p>
              Elle disparaîtra de cet écran, de l'ouverture de session et des fiches utilisateurs
              {titulaireSupprime &&
                ` (elle sera retirée à ${`${titulaireSupprime.prenom} ${titulaireSupprime.nom}`.trim()})`}
              . Ses sessions et ses ventes restent dans l'historique.
            </p>
            {supprimer.isError && (
              <p className="alerte alerte-danger" role="alert">
                {messageErreur(supprimer.error)}
              </p>
            )}
            <div className="rangee-boutons">
              <button
                type="button"
                className="bouton bouton-contour"
                onClick={() => {
                  supprimer.reset();
                  setASupprimer(null);
                }}
              >
                Annuler
              </button>
              <button
                type="button"
                className="bouton bouton-danger"
                disabled={supprimer.isPending}
                onClick={() => supprimer.mutate(aSupprimer.id, { onSuccess: () => setASupprimer(null) })}
              >
                Supprimer
              </button>
            </div>
          </div>
        </Dialogue>
      )}
      {aDesactiver && (
        <Dialogue
          titre={`Désactiver ${aDesactiver.nom} ?`}
          icone={<IconeAlerte />}
          onFermer={() => {
            basculer.reset();
            setADesactiver(null);
          }}
        >
          <div className="formulaire">
            {aDesactiver.session ? (
              <p className="alerte alerte-attention">
                Une session est ouverte sur cette caisse par {nomUtilisateur(aDesactiver.session.caissierId)}.
                Elle sera clôturée d'office, sans comptage des espèces (
                {dh(aDesactiver.session.especesAttendues)} attendues), et ses ventes en attente annulées.
              </p>
            ) : (
              <p className="alerte alerte-attention">Plus personne ne pourra ouvrir cette caisse.</p>
            )}
            <p>Vous pourrez la réactiver à tout moment avec la bascule.</p>
            {basculer.isError && (
              <p className="alerte alerte-danger" role="alert">
                {messageErreur(basculer.error)}
              </p>
            )}
            <div className="rangee-boutons">
              <button
                type="button"
                className="bouton bouton-contour"
                onClick={() => {
                  basculer.reset();
                  setADesactiver(null);
                }}
              >
                Annuler
              </button>
              <button
                type="button"
                className="bouton bouton-danger"
                disabled={basculer.isPending}
                onClick={() => changerEtat(aDesactiver, false)}
              >
                Désactiver
              </button>
            </div>
          </div>
        </Dialogue>
      )}
    </section>
  );
}

const schema = z.object({
  nom: z.string().trim().min(1, "Nom obligatoire").max(80, "80 caractères maximum"),
});
type Champs = z.infer<typeof schema>;

/** Création d'une caisse, ou renommage (clic sur son nom). */
function CaisseDialogue({ caisse, onFermer }: { caisse: Caisse | null; onFermer: () => void }) {
  const enregistrer = useEnregistrerCaisse();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Champs>({ resolver: zodResolver(schema), defaultValues: { nom: caisse?.nom ?? "" } });

  const envoyer = handleSubmit((champs) =>
    enregistrer.mutate(
      { id: caisse?.id ?? null, nom: champs.nom, active: caisse?.active ?? true },
      { onSuccess: onFermer },
    ),
  );

  return (
    <Dialogue
      titre={caisse ? `Renommer ${caisse.nom}` : "Nouvelle caisse"}
      icone={<IconeCaisse />}
      onFermer={onFermer}
    >
      <form className="formulaire" onSubmit={envoyer} noValidate>
        <label className="champ">
          <span className="libelle">Nom</span>
          <input autoFocus placeholder="Caisse 1" {...register("nom")} />
          {errors.nom && <span className="erreur-champ">{errors.nom.message}</span>}
        </label>
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
            {caisse ? "Enregistrer" : "Créer"}
          </button>
        </div>
      </form>
    </Dialogue>
  );
}
