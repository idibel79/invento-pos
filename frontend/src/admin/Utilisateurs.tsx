import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { LIBELLES_ROLE } from "../auth/roles";
import { useCaisses, useParametres } from "../caisse/requetes";
import { dateHeure } from "../ui/format";
import { EtatRequete } from "./EtatRequete";
import { useDeverrouiller, useUtilisateurs } from "./requetes";
import { SecretDialogue } from "./SecretDialogue";
import { UtilisateurDialogue } from "./UtilisateurDialogue";

type Utilisateur = Schemas["UtilisateurDto"];

type Fenetre =
  | { type: "fiche"; utilisateur: Utilisateur | null }
  | { type: "mot-de-passe" | "pin"; utilisateur: Utilisateur };

/** Comptes de la boutique : jamais supprimés, seulement désactivés. */
export function Utilisateurs() {
  const utilisateurs = useUtilisateurs();
  const caisses = useCaisses();
  const fuseau = useParametres().data?.fuseauHoraire;
  const deverrouiller = useDeverrouiller();
  const [fenetre, setFenetre] = useState<Fenetre | null>(null);
  const nomsCaisses = new Map((caisses.data ?? []).map((c) => [c.id, c.nom]));
  const fermer = () => setFenetre(null);

  return (
    <section className="admin-section">
      <div className="admin-tete">
        <div>
          <h1 className="admin-titre">Utilisateurs</h1>
          <p className="texte-secondaire">
            Le PIN à 4 chiffres permet à un responsable d'autoriser une remise sur le poste d'un caissier. Un
            compte est verrouillé après 5 échecs de connexion ou de PIN.
          </p>
        </div>
        <button
          type="button"
          className="bouton bouton-plein"
          onClick={() => setFenetre({ type: "fiche", utilisateur: null })}
        >
          Nouvel utilisateur
        </button>
      </div>
      {deverrouiller.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(deverrouiller.error)}
        </p>
      )}
      <EtatRequete requete={utilisateurs} vide="Aucun utilisateur.">
        {(liste) => (
          <ul className="liste-admin">
            {liste.map((u) => (
              <li key={u.id} className={`ligne-admin${u.actif ? "" : " inactive"}`}>
                <span className="avatar" aria-hidden="true">
                  {u.prenom.charAt(0) || u.nom.charAt(0)}
                </span>
                <div className="ligne-admin-texte">
                  <span className="ligne-admin-nom">
                    {u.prenom} {u.nom}
                    <span className="statut statut-info">{LIBELLES_ROLE[u.role]}</span>
                    {!u.actif && <span className="statut statut-gris">Désactivé</span>}
                    {u.verrouille && <span className="statut statut-danger">Verrouillé</span>}
                    {u.doitChangerMotDePasse && (
                      <span className="statut statut-attention">Mot de passe temporaire</span>
                    )}
                    {u.pinDefini && <span className="statut statut-ok">PIN défini</span>}
                  </span>
                  <span className="texte-secondaire">
                    {u.login} ·{" "}
                    {u.role === "ADMIN"
                      ? "toutes les caisses"
                      : u.caisseIds.length > 0
                        ? u.caisseIds.map((id) => nomsCaisses.get(id) ?? `#${id}`).join(", ")
                        : "aucune caisse"}{" "}
                    ·{" "}
                    {u.derniereConnexion
                      ? `connecté le ${dateHeure(u.derniereConnexion, fuseau)}`
                      : "jamais connecté"}
                  </span>
                </div>
                <div className="ligne-admin-actions">
                  {u.verrouille && (
                    <button
                      type="button"
                      className="bouton-petit danger"
                      disabled={deverrouiller.isPending}
                      onClick={() => deverrouiller.mutate(u.id)}
                    >
                      Déverrouiller
                    </button>
                  )}
                  <button
                    type="button"
                    className="bouton-petit"
                    onClick={() => setFenetre({ type: "fiche", utilisateur: u })}
                  >
                    Modifier
                  </button>
                  <button
                    type="button"
                    className="bouton-petit"
                    onClick={() => setFenetre({ type: "mot-de-passe", utilisateur: u })}
                  >
                    Mot de passe
                  </button>
                  <button
                    type="button"
                    className="bouton-petit"
                    onClick={() => setFenetre({ type: "pin", utilisateur: u })}
                  >
                    PIN
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </EtatRequete>
      {fenetre?.type === "fiche" && (
        <UtilisateurDialogue
          utilisateur={fenetre.utilisateur}
          caisses={caisses.data ?? []}
          onFermer={fermer}
        />
      )}
      {(fenetre?.type === "mot-de-passe" || fenetre?.type === "pin") && (
        <SecretDialogue type={fenetre.type} utilisateur={fenetre.utilisateur} onFermer={fermer} />
      )}
    </section>
  );
}
