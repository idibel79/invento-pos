import { useState, type Dispatch } from "react";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { dh, lireMontant, pourcent } from "../ui/format";
import { IconeAlerte, IconeRemise } from "../ui/icones";
import { reducteurPanier, versCommande, type ActionPanier, type EtatPanier, type Remise } from "./panier";
import { useAutorisateurs, useAutoriserRemise, usePanierCalcule } from "./requetes";

interface Props {
  panier: EtatPanier;
  dispatch: Dispatch<ActionPanier>;
  /** Ligne visée, ou null pour une remise sur tout le ticket. */
  produitId: number | null;
  onFermer: () => void;
}

/**
 * Remise sur une ligne ou sur le ticket, en % ou en montant. Le serveur calcule le prix remisé et
 * dit si la remise dépasse le plafond du vendeur ; le PIN d'un responsable est alors demandé.
 */
export function RemiseDialogue({ panier, dispatch, produitId, onFermer }: Props) {
  const ligne = produitId === null ? undefined : panier.lignes.find((l) => l.produitId === produitId);
  const actuelle = produitId === null ? panier.remiseTicket : (ligne?.remise ?? null);
  const [type, setType] = useState<Remise["type"]>(actuelle?.type ?? "POURCENT");
  const [saisie, setSaisie] = useState(actuelle ? String(actuelle.valeur).replace(".", ",") : "");
  const [motif, setMotif] = useState(panier.motifRemise ?? "");
  const [autorisateurId, setAutorisateurId] = useState("");
  const [pin, setPin] = useState("");

  const valeur = lireMontant(saisie);
  const remise: Remise | null = valeur === null ? null : { type, valeur };
  const action: ActionPanier =
    produitId === null
      ? { type: "remiseTicket", remise, motif: null }
      : { type: "remiseLigne", produitId, remise, motif: null };
  const essai = usePanierCalcule(versCommande(reducteurPanier(panier, action)));
  const apercu = valeur === null || essai.isError ? undefined : essai.data;
  const niveau = apercu?.autorisationRequise ?? "AUCUNE";
  const pinRequis = apercu !== undefined && niveau !== "AUCUNE" && !apercu.autorisationAcquise;
  const autorisateurs = useAutorisateurs(pinRequis);
  const autoriser = useAutoriserRemise();

  const ligneCalculee = apercu?.lignes.find((l) => l.produitId === produitId);
  const motifManquant = niveau !== "AUCUNE" && motif.trim() === "";
  const pinIncomplet = pinRequis && (autorisateurId === "" || !/^\d{4}$/.test(pin));
  const possible = apercu !== undefined && !essai.isFetching && !motifManquant && !pinIncomplet;

  async function appliquer() {
    if (pinRequis) {
      const jeton = await autoriser.mutateAsync({ autorisateurId: Number(autorisateurId), pin });
      dispatch({ type: "autorisation", jeton: jeton.jeton, autorisePar: jeton.autorisateur });
    }
    const motifRetenu = niveau === "AUCUNE" ? null : motif.trim();
    dispatch(
      produitId === null
        ? { type: "remiseTicket", remise, motif: motifRetenu }
        : { type: "remiseLigne", produitId, remise, motif: motifRetenu },
    );
    onFermer();
  }

  return (
    <Dialogue
      titre={produitId === null ? "Remise sur le ticket" : "Remise sur la ligne"}
      icone={<IconeRemise />}
      onFermer={onFermer}
      largeur={540}
    >
      <div className="encadre-gris">
        {ligne ? (
          <>
            <div className="encadre-produit">{ligne.designation}</div>
            <div className="encadre-ligne">
              <span>
                {ligne.quantite} × {dh(ligneCalculee?.prixUnitaire ?? ligne.prixUnitaire)}
              </span>
              {ligneCalculee && (
                <span>
                  {ligneCalculee.tauxTva > 0 ? `TVA ${pourcent(ligneCalculee.tauxTva)}` : "Exonéré"}
                </span>
              )}
            </div>
          </>
        ) : (
          <div className="encadre-ligne">
            <span>Total des lignes</span>
            <span>{apercu ? dh(apercu.totaux.sousTotal - apercu.totaux.remisesLignes) : "…"}</span>
          </div>
        )}
      </div>

      <div className="choix" role="group" aria-label="Type de remise">
        <button type="button" aria-pressed={type === "POURCENT"} onClick={() => setType("POURCENT")}>
          Pourcentage
        </button>
        <button type="button" aria-pressed={type === "MONTANT"} onClick={() => setType("MONTANT")}>
          Montant
        </button>
      </div>

      <div className="deux-colonnes">
        <div className="champ">
          <label className="libelle" htmlFor="valeur-remise">
            Remise
          </label>
          <div className="champ-montant">
            <input
              id="valeur-remise"
              inputMode="decimal"
              value={saisie}
              onChange={(e) => setSaisie(e.target.value)}
              autoFocus
            />
            <span>{type === "POURCENT" ? "%" : "DH"}</span>
          </div>
        </div>
        <div className="champ">
          <span className="libelle">{produitId === null ? "Total remisé" : "Prix remisé"}</span>
          <div className="prix-remise" aria-live="polite">
            {apercu && (ligneCalculee || produitId === null) ? (
              <>
                <strong>{dh(ligneCalculee ? ligneCalculee.montantNet : apercu.totaux.totalTtc)}</strong>
                <span>− {dh(ligneCalculee ? ligneCalculee.remiseMontant : apercu.totaux.remiseTicket)}</span>
              </>
            ) : (
              <span>…</span>
            )}
          </div>
        </div>
      </div>
      <p className="texte-secondaire">Une remise à 0 retire la remise. Pas de cumul avec les promotions.</p>

      {essai.isError && valeur !== null && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(essai.error)}
        </p>
      )}

      {pinRequis && (
        <>
          <div className="alerte alerte-attention">
            <IconeAlerte />
            <div>
              <strong>Autorisation {niveau === "ADMIN" ? "de l'admin" : "d'un responsable"} requise</strong>
              {apercu &&
                `Remise de ${pourcent(apercu.tauxRemiseMax)}, au-delà de votre plafond fixé par l'admin.`}
            </div>
          </div>
          <div className="deux-colonnes">
            <div className="champ">
              <label className="libelle" htmlFor="autorisateur">
                {niveau === "ADMIN" ? "Admin" : "Responsable"}
              </label>
              <select
                id="autorisateur"
                value={autorisateurId}
                onChange={(e) => setAutorisateurId(e.target.value)}
              >
                <option value="">Choisir…</option>
                {(autorisateurs.data ?? [])
                  .filter((a) => niveau !== "ADMIN" || a.role === "ADMIN")
                  .map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.prenom} {a.nom}
                    </option>
                  ))}
              </select>
            </div>
            <label className="champ">
              <span className="libelle">Code PIN</span>
              <input
                type="password"
                inputMode="numeric"
                autoComplete="off"
                maxLength={4}
                value={pin}
                onChange={(e) => setPin(e.target.value.replace(/\D/g, ""))}
              />
            </label>
          </div>
        </>
      )}

      {niveau !== "AUCUNE" && (
        <label className="champ">
          <span className="libelle">Motif</span>
          <input
            maxLength={255}
            value={motif}
            onChange={(e) => setMotif(e.target.value)}
            placeholder="Obligatoire, enregistré au journal"
          />
        </label>
      )}

      {autoriser.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(autoriser.error)}
        </p>
      )}
      <div className="rangee-boutons">
        <button type="button" className="bouton bouton-contour" onClick={onFermer}>
          Annuler
        </button>
        <button
          type="button"
          className="bouton bouton-plein"
          disabled={!possible || autoriser.isPending}
          onClick={() => void appliquer().catch(() => undefined)}
        >
          {pinRequis ? "Autoriser et appliquer" : "Appliquer"}
        </button>
      </div>
    </Dialogue>
  );
}
