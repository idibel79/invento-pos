import { useEffect, useReducer, useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useUtilisateur } from "../auth/AuthContext";
import { ClotureDialogue } from "../caisse/ClotureDialogue";
import { useImpressionFacture } from "../facture/FactureA4";
import { useParametres } from "../caisse/requetes";
import { Dialogue } from "../ui/Dialogue";
import { decimal, dh, heure } from "../ui/format";
import { useMaintenant } from "../ui/hooks";
import { IconeValide } from "../ui/icones";
import { IconeInvento } from "../ui/Logo";
import { AttenteDialogue } from "./AttenteDialogue";
import { Catalogue } from "./Catalogue";
import { EncaissementDialogue } from "./EncaissementDialogue";
import { MenuUtilisateur } from "./MenuUtilisateur";
import { panierVide, reducteurPanier, versCommande } from "./panier";
import { PanierPanel } from "./PanierPanel";
import { RemiseDialogue } from "./RemiseDialogue";
import { TicketAImprimer } from "./TicketCaisse";
import { useAbandonnerVente, usePanierCalcule, useVentesEnAttente } from "./requetes";
import { VentesEnAttenteDialogue } from "./VentesEnAttenteDialogue";

type Fenetre =
  | { type: "encaissement" }
  | { type: "remise"; produitId: number | null }
  | { type: "attente" }
  | { type: "liste-attente" }
  | { type: "cloture" }
  | { type: "validee"; vente: Schemas["VenteDto"]; entreprise: boolean };

/** Écran de vente (direction « Table pro ») : barre de caisse en haut, tableau des produits, ticket à droite. */
interface Props {
  session: Schemas["SessionCaisseDto"];
  /** Ouvre l'administration (rubriques selon le rôle). */
  onAdministration: () => void;
}

export function EcranVente({ session, onAdministration }: Props) {
  const parametres = useParametres();
  const [panier, dispatch] = useReducer(reducteurPanier, panierVide);
  const commande = versCommande(panier);
  const calcul = usePanierCalcule(commande);
  const enAttente = useVentesEnAttente();
  const abandonner = useAbandonnerVente();
  const [fenetre, setFenetre] = useState<Fenetre | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const maintenant = useMaintenant(30_000);
  const fuseau = parametres.data?.fuseauHoraire;
  const utilisateur = useUtilisateur();
  const nomVendeur = `${utilisateur.prenom} ${utilisateur.nom}`.trim();
  const facture = useImpressionFacture();

  useEffect(() => {
    if (!message) return;
    const minuterie = setTimeout(() => setMessage(null), 4000);
    return () => clearTimeout(minuterie);
  }, [message]);

  const fermer = () => setFenetre(null);
  const nombreEnAttente = enAttente.data?.length ?? 0;
  const vider = () => {
    // Ticket repris vidé : la vente est abandonnée au lieu de rester ouverte jusqu'à la clôture.
    if (panier.venteEnAttenteId !== null) abandonner.mutate(panier.venteEnAttenteId);
    dispatch({ type: "vider" });
  };
  const total = calcul.data?.totaux.totalTtc ?? 0;
  const tiroir = session.tiroir;
  const ventes = (tiroir?.ventesEspeces ?? session.especesEncaissees ?? 0) + (tiroir?.ventesCarte ?? 0);
  const mouvements = (tiroir?.recu ?? 0) - (tiroir?.verse ?? 0);

  return (
    <div className="ecran-vente">
      <header className="barre-haut">
        <span className="barre-marque">
          <IconeInvento />
          Invento POS
        </span>
        <span className="barre-session">
          <strong>{session.caisseNom}</strong>
          <span>ouverte à {heure(session.ouverture, fuseau)}</span>
        </span>
        <span className="barre-tiroir">
          <span className="barre-tiroir-total">
            <span>Dans le tiroir</span>
            <strong>{dh(tiroir?.attendu ?? session.fondInitial + ventes)}</strong>
          </span>
          <span className="barre-tiroir-detail">
            fond {decimal(session.fondInitial)}
            <br />+ ventes {decimal(ventes)}
            {mouvements !== 0 && ` ${mouvements > 0 ? "+" : "−"} versements ${decimal(Math.abs(mouvements))}`}
          </span>
          <button
            type="button"
            className="barre-bouton"
            aria-label="Clôturer la caisse"
            onClick={() => setFenetre({ type: "cloture" })}
          >
            Clôturer
          </button>
        </span>
        <span className="barre-espace" />
        <button
          type="button"
          className={`barre-bouton barre-attente${nombreEnAttente > 0 ? " signalee" : ""}`}
          aria-label={`En attente (${nombreEnAttente})`}
          onClick={() => setFenetre({ type: "liste-attente" })}
        >
          {nombreEnAttente > 0
            ? `${nombreEnAttente} vente${nombreEnAttente > 1 ? "s" : ""} en attente`
            : "Aucune vente en attente"}
        </button>
        <span className="barre-heure">{heure(new Date(maintenant).toISOString(), fuseau)}</span>
        <MenuUtilisateur enCaisse onAdministration={onAdministration} />
      </header>

      <main className="corps-vente">
        <Catalogue onAjouter={(p) => dispatch({ type: "ajouter", produit: p })} onMessage={setMessage} />
        <PanierPanel
          panier={panier}
          dispatch={dispatch}
          calcul={calcul}
          onRemiseLigne={(produitId) => setFenetre({ type: "remise", produitId })}
          onRemiseTicket={() => setFenetre({ type: "remise", produitId: null })}
          onAttente={() => setFenetre({ type: "attente" })}
          onEncaisser={() => setFenetre({ type: "encaissement" })}
          onVider={vider}
        />
      </main>

      {fenetre?.type === "encaissement" && (
        <EncaissementDialogue
          commande={commande}
          total={total}
          apercu={calcul.data}
          onFermer={fermer}
          onValidee={(vente, entreprise) => {
            dispatch({ type: "vider" });
            setFenetre({ type: "validee", vente, entreprise });
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
          {fenetre.vente.paiements.length > 1 && (
            <div className="rendu">
              <span className="libelle">
                Espèces {dh(fenetre.vente.paiements[0]?.montant ?? 0)} · carte bancaire
              </span>
              <strong data-testid="part-carte">{dh(fenetre.vente.paiements[1]?.montant ?? 0)}</strong>
            </div>
          )}
          {fenetre.vente.paiements.length === 1 && fenetre.vente.paiement?.mode === "CARTE" && (
            <div className="rendu">
              <span className="libelle">Payé par carte bancaire</span>
            </div>
          )}
          {fenetre.vente.paiements.length === 1 && fenetre.vente.paiement?.mode === "ESPECES" && (
            <div className="rendu">
              <span className="libelle">Rendu monnaie</span>
              <strong data-testid="rendu">{dh(fenetre.vente.paiement.rendu ?? 0)}</strong>
            </div>
          )}
          <div className="rangee-boutons">
            <button type="button" className="bouton bouton-contour" onClick={() => window.print()}>
              Imprimer le ticket
            </button>
            {fenetre.entreprise && (
              <button
                type="button"
                className="bouton bouton-contour"
                disabled={facture.enCours}
                onClick={() => facture.imprimer(fenetre.vente.id)}
              >
                Facture A4
              </button>
            )}
            <button type="button" className="bouton bouton-plein" onClick={fermer} autoFocus>
              Nouvelle vente
            </button>
          </div>
          <TicketAImprimer
            vente={fenetre.vente}
            boutique={parametres.data}
            vendeur={nomVendeur}
            fuseau={fuseau}
          />
          {facture.erreur && (
            <p className="alerte alerte-danger" role="alert">
              {messageErreur(facture.erreur)}
            </p>
          )}
          {facture.rendu}
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
