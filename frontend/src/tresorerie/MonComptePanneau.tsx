import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { messageErreur } from "../api/erreurs";
import { useUtilisateur } from "../auth/AuthContext";
import { useParametres } from "../caisse/requetes";
import { decimal, dh, heure, lireMontant } from "../ui/format";
import { PageMonCompte } from "./PageMonCompte";
import { nouvelleCle, useAnnulerVersement, useMonCompte, useVerserALaPrincipale } from "./requetes";
import { StatutVersement } from "./StatutVersement";
import { VersementAValider } from "./VersementAValider";

/**
 * Bouton « Mon compte · solde » avec la pastille des versements à valider. Depuis la barre de caisse,
 * il ouvre le panneau latéral ; depuis l'écran d'ouverture ({@code page}), la page Mon compte.
 */
export function BoutonMonCompte({ classe, page = false }: { classe: string; page?: boolean }) {
  const compte = useMonCompte();
  const [panneau, setPanneau] = useState(false);
  const [historique, setHistorique] = useState(false);
  const aValider = compte.data?.aValider.length ?? 0;
  return (
    <>
      <button
        type="button"
        className={`${classe} bouton-mon-compte`}
        onClick={() => (page ? setHistorique(true) : setPanneau(true))}
        aria-label={`Mon compte${aValider > 0 ? `, ${aValider} versement${aValider > 1 ? "s" : ""} à valider` : ""}`}
      >
        Mon compte{compte.data ? ` · ${dh(compte.data.solde)}` : ""}
        {aValider > 0 && <span className="pastille-nombre">{aValider}</span>}
      </button>
      {historique && (
        <PageMonCompte
          retour={page ? "Retour à l'ouverture" : "Retour à la caisse"}
          onFermer={() => setHistorique(false)}
        />
      )}
      {panneau && (
        <MonComptePanneau
          onFermer={() => setPanneau(false)}
          onHistorique={
            historique
              ? undefined
              : () => {
                  setPanneau(false);
                  setHistorique(true);
                }
          }
        />
      )}
    </>
  );
}

/**
 * Panneau latéral « Mon compte », par-dessus l'écran : solde dû à la caisse principale, attendu dans le
 * tiroir, versements reçus à valider, versement à la caisse principale et historique du jour.
 */
export function MonComptePanneau({
  onFermer,
  onHistorique,
}: {
  onFermer: () => void;
  onHistorique?: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const utilisateur = useUtilisateur();
  const fuseau = useParametres().data?.fuseauHoraire;
  const compte = useMonCompte();
  const verser = useVerserALaPrincipale();
  const annuler = useAnnulerVersement();
  const [saisie, setSaisie] = useState("");
  const [cle, setCle] = useState(nouvelleCle);
  const montant = lireMontant(saisie);
  const donnees = compte.data;
  const depasse = montant !== null && donnees !== undefined && montant > donnees.solde;

  useEffect(() => {
    const dialogue = ref.current;
    if (dialogue && !dialogue.open) dialogue.showModal?.();
  }, []);

  // Portail : le panneau s'ouvre aussi depuis le formulaire d'ouverture, sans y imbriquer ses formulaires.
  return createPortal(
    <dialog
      ref={ref}
      className="panneau-lateral"
      aria-label="Mon compte"
      onCancel={(e) => {
        e.preventDefault();
        onFermer();
      }}
      onClick={(e) => {
        // Clic sur le voile (en dehors du panneau) : fermeture.
        if (e.target === ref.current) onFermer();
      }}
    >
      <div className="panneau-contenu">
        <div className="dialogue-tete">
          <div>
            <h2 className="titre-popup">Mon compte</h2>
            <p className="dialogue-sous-titre">
              {utilisateur.prenom} {utilisateur.nom}
            </p>
          </div>
          <button type="button" className="dialogue-fermer" aria-label="Fermer" onClick={onFermer}>
            ×
          </button>
        </div>

        {compte.isPending && <p className="chargement">Chargement…</p>}
        {compte.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(compte.error)}
          </p>
        )}
        {donnees && (
          <div className="panneau-corps">
            <div className="tuiles">
              <div className="tuile">
                <span className="indicateur-libelle">Solde dû</span>
                <strong>{dh(donnees.solde)}</strong>
                <span className="indicateur-libelle">à la caisse principale</span>
              </div>
              <div className="tuile">
                <span className="indicateur-libelle">Dans le tiroir (attendu)</span>
                <strong>{donnees.tiroirAttendu == null ? "—" : dh(donnees.tiroirAttendu)}</strong>
                <span className="indicateur-libelle">
                  {donnees.sessionOuverte ? "espèces + tickets TPE" : "aucune caisse ouverte"}
                </span>
              </div>
            </div>

            <section className="panneau-bloc">
              <h3 className="panneau-titre">À valider</h3>
              {donnees.aValider.length === 0 ? (
                <p className="vide">Aucun versement à valider.</p>
              ) : (
                <ul className="liste-a-valider">
                  {donnees.aValider.map((v) => (
                    <VersementAValider
                      key={v.id}
                      versement={v}
                      emetteur="Caisse principale"
                      fuseau={fuseau}
                    />
                  ))}
                </ul>
              )}
            </section>

            <section className="panneau-bloc">
              <h3 className="panneau-titre">Verser à la caisse principale</h3>
              <form
                className="panneau-versement"
                onSubmit={(e) => {
                  // Les événements React traversent le portail : l'envoi ne doit pas atteindre l'écran parent.
                  e.preventDefault();
                  e.stopPropagation();
                  if (montant === null || montant <= 0 || depasse) return;
                  verser.mutate(
                    { montant, cle },
                    {
                      onSuccess: () => {
                        setSaisie("");
                        setCle(nouvelleCle());
                      },
                    },
                  );
                }}
              >
                <div className="champ-montant champ-montant-compact">
                  <input
                    aria-label="Montant à verser"
                    inputMode="decimal"
                    autoComplete="off"
                    placeholder={decimal(0)}
                    value={saisie}
                    onChange={(e) => setSaisie(e.target.value)}
                  />
                  <span>DH</span>
                </div>
                <button
                  type="submit"
                  className="bouton bouton-plein"
                  disabled={montant === null || montant <= 0 || depasse || verser.isPending}
                >
                  Verser
                </button>
              </form>
              {depasse && (
                <p className="erreur-champ">Le montant dépasse votre solde ({dh(donnees.solde)}).</p>
              )}
              {verser.isError && (
                <p className="erreur-champ" role="alert">
                  {messageErreur(verser.error)}
                </p>
              )}
              <p className="texte-secondaire">
                {utilisateur.role === "ADMIN"
                  ? "Vous tenez la caisse principale : votre versement est auto-validé."
                  : "Le versement reste en transit jusqu'à ce que l'admin confirme l'avoir reçu."}
              </p>
            </section>

            <section className="panneau-bloc">
              <div className="panneau-titre-ligne">
                <h3 className="panneau-titre">Aujourd'hui</h3>
                {onHistorique && (
                  <button type="button" className="bouton-lien" onClick={onHistorique}>
                    Tout l'historique
                  </button>
                )}
              </div>
              {annuler.isError && (
                <p className="erreur-champ" role="alert">
                  {messageErreur(annuler.error)}
                </p>
              )}
              {donnees.historique.length === 0 ? (
                <p className="vide">Aucun versement aujourd'hui.</p>
              ) : (
                <ul className="historique-versements">
                  {donnees.historique.map((v) => (
                    <li key={v.id}>
                      <span className="historique-heure">{heure(v.creeLe, fuseau)}</span>
                      <span className="historique-libelle">
                        {v.versLaPrincipale ? "Versé à la caisse principale" : "Reçu de la caisse principale"}
                        {v.cloture && " (clôture)"}
                        {v.statut === "REFUSE" && v.motif && (
                          <span className="a-valider-motif">{v.motif}</span>
                        )}
                      </span>
                      <span className={`historique-montant ${v.versLaPrincipale ? "negatif" : "positif"}`}>
                        {v.versLaPrincipale ? "−" : "+"}
                        {dh(v.montant)}
                      </span>
                      <StatutVersement
                        statut={v.statut}
                        autoValide={v.autoValide}
                        versLaPrincipale={v.versLaPrincipale}
                      />
                      {v.versLaPrincipale && v.statut === "EN_TRANSIT" ? (
                        <button
                          type="button"
                          className="bouton-lien"
                          disabled={annuler.isPending}
                          onClick={() => annuler.mutate(v.id)}
                        >
                          Annuler
                        </button>
                      ) : (
                        <span />
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        )}
      </div>
    </dialog>,
    document.body,
  );
}
