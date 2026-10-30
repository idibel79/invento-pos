import { useRef, useState, type KeyboardEvent } from "react";
import type { Schemas } from "../api/client";
import { ErreurApi, messageErreur } from "../api/erreurs";
import { chercherParCodeBarre, useCategories, useProduits, useZones } from "../catalogue/requetes";
import { dh } from "../ui/format";
import { useValeurDifferee } from "../ui/hooks";
import { IconeScan } from "../ui/icones";

type Produit = Schemas["ProduitDto"];

interface Props {
  onAjouter: (produit: Produit) => void;
  onMessage: (message: string) => void;
}

/** Recherche et scan, filtres par zone et catégorie, grille des produits (rupture grisée). */
export function Catalogue({ onAjouter, onMessage }: Props) {
  const champ = useRef<HTMLInputElement>(null);
  const [saisie, setSaisie] = useState("");
  const [zoneId, setZoneId] = useState<number | null>(null);
  const [categorieId, setCategorieId] = useState<number | null>(null);
  const recherche = useValeurDifferee(saisie.trim(), 250);
  const zones = useZones();
  const categories = useCategories();
  const produits = useProduits({ recherche, zoneId, categorieId });
  const liste = produits.data?.pages.flatMap((p) => p.contenu) ?? [];

  function ajouter(produit: Produit) {
    if (produit.enRupture) {
      onMessage(`Rupture : ${produit.designation}`);
      return;
    }
    onAjouter(produit);
  }

  // La douchette tape le code puis Entrée : recherche immédiate, sans délai.
  async function valider(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key !== "Enter") return;
    e.preventDefault();
    const code = saisie.trim();
    if (!code) return;
    try {
      ajouter(await chercherParCodeBarre(code));
      setSaisie("");
    } catch (erreur) {
      // Pas un code-barre connu : le texte reste une recherche dans la grille.
      if (!(erreur instanceof ErreurApi && erreur.statut === 404)) onMessage(messageErreur(erreur));
      else if (/^\d{6,}$/.test(code)) onMessage(`Aucun produit pour le code-barre ${code}`);
    }
    champ.current?.focus();
  }

  return (
    <section className="catalogue" aria-label="Catalogue">
      <label className="recherche">
        <IconeScan />
        <span className="visuellement-cache">Scanner ou rechercher</span>
        <input
          ref={champ}
          value={saisie}
          onChange={(e) => setSaisie(e.target.value)}
          onKeyDown={(e) => void valider(e)}
          placeholder="Scanner un code-barre ou rechercher (nom, PID)"
          autoFocus
          autoComplete="off"
        />
        <span className="badge-douchette">Douchette prête</span>
      </label>

      <Filtre titre="Zones" options={zones.data ?? []} valeur={zoneId} onChoisir={(id) => setZoneId(id)} />
      <Filtre
        titre="Catégories"
        options={categories.data ?? []}
        valeur={categorieId}
        onChoisir={(id) => setCategorieId(id)}
      />

      {produits.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(produits.error)}
        </p>
      )}
      {produits.isSuccess && liste.length === 0 && <p className="vide">Aucun produit ne correspond.</p>}
      <div className="grille-produits">
        {liste.map((p) => (
          <CarteProduit key={p.id} produit={p} onChoisir={() => ajouter(p)} />
        ))}
      </div>
      {produits.hasNextPage && (
        <div className="rangee-boutons">
          <button
            type="button"
            className="bouton bouton-contour"
            onClick={() => void produits.fetchNextPage()}
            disabled={produits.isFetchingNextPage}
          >
            Afficher plus de produits
          </button>
        </div>
      )}
    </section>
  );
}

function Filtre({
  titre,
  options,
  valeur,
  onChoisir,
}: {
  titre: string;
  options: Schemas["ReferenceDto"][];
  valeur: number | null;
  onChoisir: (id: number | null) => void;
}) {
  return (
    <div className="filtres" role="group" aria-label={titre}>
      <span className="libelle">{titre}</span>
      <div className="puces">
        <button type="button" className="puce" aria-pressed={valeur === null} onClick={() => onChoisir(null)}>
          Toutes
        </button>
        {options.map((o) => (
          <button
            key={o.id}
            type="button"
            className="puce"
            aria-pressed={valeur === o.id}
            onClick={() => onChoisir(o.id)}
          >
            {o.nom}
          </button>
        ))}
      </div>
    </div>
  );
}

function CarteProduit({ produit, onChoisir }: { produit: Produit; onChoisir: () => void }) {
  const stock = produit.stockDisponible;
  const classeStock = produit.enRupture ? "stock-rupture" : stock <= 2 ? "stock-bas" : "stock-ok";
  return (
    <button type="button" className="carte-produit" disabled={produit.enRupture} onClick={onChoisir}>
      <span className="carte-produit-haut">
        <span className="carte-produit-zone">{produit.zone.nom}</span>
        <span className={`stock ${classeStock}`}>{produit.enRupture ? "Rupture" : `Stock ${stock}`}</span>
      </span>
      <span className="carte-produit-nom">{produit.designation}</span>
      <span className="carte-produit-bas">
        <span className="carte-produit-prix">{dh(produit.ppv)}</span>
        <span className="carte-produit-tva">
          {produit.tva.taux > 0 ? `TVA ${produit.tva.taux} %` : "Exonéré"}
        </span>
      </span>
    </button>
  );
}
