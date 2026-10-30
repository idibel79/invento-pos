import { useEffect, useReducer, useState } from "react";
import type { Schemas } from "../api/client";
import { useAuth, useUtilisateur } from "../auth/AuthContext";
import { ClotureDialogue } from "../caisse/ClotureDialogue";
import { useParametres } from "../caisse/requetes";
import { Dialogue } from "../ui/Dialogue";
import { dh, heure, pourcent } from "../ui/format";
import { useMaintenant } from "../ui/hooks";
import { IconeValide } from "../ui/icones";
import { Logo } from "../ui/Logo";
import { AttenteDialogue } from "./AttenteDialogue";
import { Catalogue } from "./Catalogue";
import { EncaissementDialogue } from "./EncaissementDialogue";
import { panierVide, reducteurPanier, versCommande } from "./panier";
import { PanierPanel } from "./PanierPanel";
import { RemiseDialogue } from "./RemiseDialogue";
import { usePanierCalcule, useVentesEnAttente } from "./requetes";
import { VentesEnAttenteDialogue } from "./VentesEnAttenteDialogue";

type Fenetre =
  | { type: "encaissement" }
  | { type: "remise"; produitId: number | null }
  | { type: "attente" }
  | { type: "liste-attente" }
  | { type: "cloture" }
  | { type: "validee"; vente: Schemas["VenteDto"] };

const LIBELLES_ROLE = { CAISSIER: "Caissier", RESPONSABLE: "Responsable", ADMIN: "Admin" } as const;

/** Écran de vente (maquette 1) : catalogue à gauche, ticket en cours à droite. */
export function EcranVente({ session }: { session: Schemas["SessionCaisseDto"] }) {
  const utilisateur = useUtilisateur();
  const { deconnecter } = useAuth();
  const parametres = useParametres();
  const [panier, dispatch] = useReducer(reducteurPanier, panierVide);
  const commande = versCommande(panier);
  const calcul = usePanierCalcule(commande);
  const enAttente = useVentesEnAttente();
  const [fenetre, setFenetre] = useState<Fenetre | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const maintenant = useMaintenant(30_000);
  const fuseau = parametres.data?.fuseauHoraire;
  const plafond =
    utilisateur.role === "CAISSIER"
      ? parametres.data?.plafondRemiseCaissier
      : utilisateur.role === "RESPONSABLE"
        ? parametres.data?.plafondRemiseResponsable
        : 100;

  useEffect(() => {
    if (!message) return;
    const minuterie = setTimeout(() => setMessage(null), 4000);
    return () => clearTimeout(minuterie);
  }, [message]);

  const fermer = () => setFenetre(null);
  const nombreEnAttente = enAttente.data?.length ?? 0;
  const total = calcul.data?.totaux.totalTtc ?? 0;

  return (
    <div className="ecran-vente">
      <header className="entete">
        <Logo />
        <span className="pastille">{session.caisseNom}</span>
        <span className="entete-espace" />
        <span className="entete-session">
          Session ouverte à {heure(session.ouverture, fuseau)} · fond {dh(session.fondInitial)}
        </span>
        <button type="button" className="bouton-petit" onClick={() => setFenetre({ type: "liste-attente" })}>
          En attente ({nombreEnAttente})
        </button>
        <button type="button" className="bouton-petit" onClick={() => setFenetre({ type: "cloture" })}>
          Clôturer
        </button>
        <div className="entete-utilisateur">
          <span className="avatar" aria-hidden="true">
            {utilisateur.prenom.charAt(0) || utilisateur.nom.charAt(0)}
          </span>
          <div>
            <div className="utilisateur-nom">
              {utilisateur.prenom} {utilisateur.nom}
            </div>
            <div className="utilisateur-role">
              {LIBELLES_ROLE[utilisateur.role]}
              {plafond !== undefined && ` · remise max ${pourcent(plafond)}`} ·{" "}
              {heure(new Date(maintenant).toISOString(), fuseau)}
            </div>
          </div>
          <button type="button" className="bouton-petit" onClick={() => void deconnecter()}>
            Déconnexion
          </button>
        </div>
      </header>

      <div className="corps-vente">
        <Catalogue onAjouter={(p) => dispatch({ type: "ajouter", produit: p })} onMessage={setMessage} />
        <PanierPanel
          panier={panier}
          dispatch={dispatch}
          calcul={calcul}
          onRemiseLigne={(produitId) => setFenetre({ type: "remise", produitId })}
          onRemiseTicket={() => setFenetre({ type: "remise", produitId: null })}
          onAttente={() => setFenetre({ type: "attente" })}
          onEncaisser={() => setFenetre({ type: "encaissement" })}
        />
      </div>

      {fenetre?.type === "encaissement" && (
        <EncaissementDialogue
          commande={commande}
          total={total}
          onFermer={fermer}
          onValidee={(vente) => {
            dispatch({ type: "vider" });
            setFenetre({ type: "validee", vente });
          }}
        />
      )}
      {fenetre?.type === "remise" && (
        <RemiseDialogue panier={panier} dispatch={dispatch} produitId={fenetre.produitId} onFermer={fermer} />
      )}
      {fenetre?.type === "attente" && (
        <AttenteDialogue
          commande={commande}
          onFermer={fermer}
          onMiseEnAttente={(vente) => {
            dispatch({ type: "vider" });
            fermer();
            setMessage(`Vente ${vente.numero} mise en attente (stock réservé 5 min).`);
          }}
        />
      )}
      {fenetre?.type === "liste-attente" && (
        <VentesEnAttenteDialogue
          panierVide={panier.lignes.length === 0}
          onFermer={fermer}
          onReprise={(vente) => {
            dispatch({ type: "reprendre", vente });
            fermer();
          }}
        />
      )}
      {fenetre?.type === "cloture" && (
        <ClotureDialogue session={session} nombreEnAttente={nombreEnAttente} onFermer={fermer} />
      )}
      {fenetre?.type === "validee" && (
        <Dialogue
          titre={`Vente ${fenetre.vente.numero} validée`}
          icone={<IconeValide />}
          ton="succes"
          onFermer={fermer}
        >
          <div className="encadre">
            <span className="libelle">Total encaissé</span>
            <span className="encadre-montant">{dh(fenetre.vente.totaux.totalTtc)}</span>
          </div>
          {fenetre.vente.paiement && (
            <div className="rendu">
              <span className="libelle">Rendu monnaie</span>
              <strong data-testid="rendu">{dh(fenetre.vente.paiement.rendu ?? 0)}</strong>
            </div>
          )}
          <div className="rangee-boutons">
            <button type="button" className="bouton bouton-plein" onClick={fermer} autoFocus>
              Nouvelle vente
            </button>
          </div>
        </Dialogue>
      )}
      {message && (
        <div className="message-flottant" role="status">
          {message}
        </div>
      )}
    </div>
  );
}
