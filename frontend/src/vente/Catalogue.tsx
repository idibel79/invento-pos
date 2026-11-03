import { useRef, useState, type CSSProperties, type KeyboardEvent } from "react";
import type { Schemas } from "../api/client";
import { ErreurApi, messageErreur } from "../api/erreurs";
import {
  chercherParCodeBarre,
  useCategories,
  useProduits,
  useZones,
  type TriCatalogue,
} from "../catalogue/requetes";
import { dh } from "../ui/format";
import { useTailleRetenue, useValeurDifferee } from "../ui/hooks";
import { IconeScan } from "../ui/icones";
import { Separateur } from "../ui/Separateur";

type Produit = Schemas["ProduitDto"];

interface Props {
  onAjouter: (produit: Produit) => void;
  onMessage: (message: string) => void;
}

/** Recherche et scan, filtres zone et catégorie, tableau des produits (rupture grisée). */
export function Catalogue({ onAjouter, onMessage }: Props) {
  const champ = useRef<HTMLInputElement>(null);
  const [saisie, setSaisie] = useState("");
  const [zoneId, setZoneId] = useState<number | null>(null);
  const [categorieId, setCategorieId] = useState<number | null>(null);
  const recherche = useValeurDifferee(saisie.trim(), 250);
  const zones = useZones();
  const categories = useCategories();
  const [tri, setTri] = useState<TriCatalogue | null>(null);
  const produits = useProduits({ recherche, zoneId, categorieId, tri });

  // Clics successifs sur une colonne : croissant, décroissant, puis tri annulé (ordre par défaut).
  function trierPar(champ: TriCatalogue["champ"]) {
    setTri((t) => {
      if (t?.champ !== champ) return { champ, ordre: "asc" };
      return t.ordre === "asc" ? { champ, ordre: "desc" } : null;
    });
  }
  const etatTri = (champ: TriCatalogue["champ"]) => ({
    ordre: tri?.champ === champ ? tri.ordre : null,
    onTrier: () => trierPar(champ),
  });
  const liste = produits.data?.pages.flatMap((p) => p.contenu) ?? [];
  // Largeur de chaque colonne, réglée en tirant le bord de son en-tête et retenue par le navigateur.
  const designation = useTailleRetenue("invento-pos.colonne-designation", 340);
  const zone = useTailleRetenue("invento-pos.colonne-zone", 160);
  const categorie = useTailleRetenue("invento-pos.colonne-categorie", 140);
  const stock = useTailleRetenue("invento-pos.colonne-stock", 72);

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
      <div className="barre-outils">
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
          {saisie && (
            <button
              type="button"
              className="recherche-effacer"
              aria-label="Effacer la recherche"
              onClick={() => {
                setSaisie("");
                champ.current?.focus();
              }}
            >
              ×
            </button>
          )}
          <span className="badge-douchette">Douchette prête</span>
        </label>
        <Filtre libelle="Zone" options={zones.data ?? []} valeur={zoneId} onChoisir={setZoneId} />
        <Filtre
          libelle="Catégorie"
          options={categories.data ?? []}
          valeur={categorieId}
          onChoisir={setCategorieId}
        />
      </div>

      {produits.isError && (
        <p className="alerte alerte-danger" role="alert">
          {messageErreur(produits.error)}
        </p>
      )}
      <div
        className="tableau-produits"
        style={
          {
            "--colonnes": `${designation[0]}px ${zone[0]}px ${categorie[0]}px ${stock[0]}px minmax(110px, 1fr)`,
          } as CSSProperties
        }
      >
        <div className="tableau-entete">
          <EnteteColonne libelle="Désignation" taille={designation} min={160} tri={etatTri("designation")} />
          <EnteteColonne libelle="Zone" taille={zone} min={80} />
          <EnteteColonne libelle="Catégorie" taille={categorie} min={80} />
          <EnteteColonne libelle="Stock" taille={stock} min={56} aDroite />
          {/* Dernière colonne : elle occupe la place restante, sans poignée. */}
          <span className="entete-colonne a-droite" aria-sort={ariaTri(etatTri("ppv").ordre)}>
            <BoutonTri libelle="Prix TTC" tri={etatTri("ppv")} />
          </span>
        </div>
        {produits.isSuccess && liste.length === 0 && <p className="vide">Aucun produit ne correspond.</p>}
        {liste.map((p) => (
          <LigneProduit key={p.id} produit={p} onChoisir={() => ajouter(p)} />
        ))}
        {produits.hasNextPage && (
          <button
            type="button"
            className="tableau-suite"
            onClick={() => void produits.fetchNextPage()}
            disabled={produits.isFetchingNextPage}
          >
            Afficher plus de produits
          </button>
        )}
      </div>
    </section>
  );
}

/** Filtre en liste déroulante (zone, catégorie). */
function Filtre({
  libelle,
  options,
  valeur,
  onChoisir,
}: {
  libelle: string;
  options: Schemas["ReferenceDto"][];
  valeur: number | null;
  onChoisir: (id: number | null) => void;
}) {
  return (
    <label className="filtre">
      <span>{libelle} :</span>
      <select
        value={valeur ?? ""}
        onChange={(e) => onChoisir(e.target.value === "" ? null : Number(e.target.value))}
      >
        <option value="">Toutes</option>
        {options.map((o) => (
          <option key={o.id} value={o.id}>
            {o.nom}
          </option>
        ))}
      </select>
    </label>
  );
}

/** Ordre de la colonne (null : la liste est triée par une autre colonne). */
interface EtatTri {
  ordre: "asc" | "desc" | null;
  onTrier: () => void;
}

function ariaTri(ordre: EtatTri["ordre"]) {
  return ordre === "asc" ? "ascending" : ordre === "desc" ? "descending" : "none";
}

/** Titre de colonne cliquable : deux triangles pleins, celui du sens actif en foncé (aucun si la colonne n'est pas triée). */
function BoutonTri({ libelle, tri }: { libelle: string; tri: EtatTri }) {
  return (
    <button type="button" className={`entete-tri${tri.ordre ? " actif" : ""}`} onClick={tri.onTrier}>
      {libelle}
      <svg className="icone-tri" width="10" height="14" viewBox="0 0 10 14" aria-hidden="true">
        <path d="M5 0L10 5.5H0Z" className={tri.ordre === "asc" ? "tri-actif" : undefined} />
        <path d="M5 14L0 8.5H10Z" className={tri.ordre === "desc" ? "tri-actif" : undefined} />
      </svg>
    </button>
  );
}

/** En-tête de colonne avec, sur son bord droit, une poignée pour en changer la largeur. */
function EnteteColonne({
  libelle,
  taille: [largeur, setLargeur],
  min,
  aDroite = false,
  tri,
}: {
  libelle: string;
  taille: [number, (largeur: number) => void];
  min: number;
  aDroite?: boolean;
  /** Colonne triable : un clic sur le titre trie la liste par cette colonne. */
  tri?: EtatTri;
}) {
  return (
    <span
      className={`entete-colonne${aDroite ? " a-droite" : ""}`}
      aria-sort={tri ? ariaTri(tri.ordre) : undefined}
    >
      {tri ? <BoutonTri libelle={libelle} tri={tri} /> : libelle}
      <Separateur
        libelle={`Largeur de la colonne ${libelle}`}
        largeur={largeur}
        min={min}
        max={800}
        sens={1}
        onLargeur={setLargeur}
      />
    </span>
  );
}

/** Une ligne du tableau : toute la ligne ajoute le produit au ticket (rupture désactivée). */
function LigneProduit({ produit, onChoisir }: { produit: Produit; onChoisir: () => void }) {
  const stock = produit.stockDisponible;
  // Rouge sous 5, orange de 5 à 10, vert au-delà.
  const classeStock = produit.enRupture
    ? "stock-rupture"
    : stock < 5
      ? "stock-faible"
      : stock <= 10
        ? "stock-moyen"
        : "stock-ok";
  return (
    <button
      type="button"
      className="ligne-produit"
      // Nom lu par les lecteurs d'écran : les cellules collées (« stock 2 » + « 34,60 DH ») seraient ambiguës.
      aria-label={`${produit.designation}, ${produit.zone.nom}, ${produit.categorie.nom}, ${produit.enRupture ? "rupture" : `stock ${stock}`}, ${dh(produit.ppv)}`}
      disabled={produit.enRupture}
      onClick={onChoisir}
    >
      <span className="ligne-produit-nom">{produit.designation}</span>
      <span className="ligne-produit-zone">{produit.zone.nom}</span>
      <span className="ligne-produit-zone">{produit.categorie.nom}</span>
      <span className={`stock a-droite ${classeStock}`}>{produit.enRupture ? "Rupture" : stock}</span>
      <span className="ligne-produit-prix a-droite">{dh(produit.ppv)}</span>
    </button>
  );
}
