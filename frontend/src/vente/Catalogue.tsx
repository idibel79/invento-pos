import { useEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from "react";
import type { Schemas } from "../api/client";
import { ErreurApi, messageErreur } from "../api/erreurs";
import { useUtilisateur } from "../auth/AuthContext";
import {
  chercherParCodeBarre,
  useBornesPrix,
  useCategories,
  useProduits,
  useZones,
  type TriCatalogue,
} from "../catalogue/requetes";
import { dh } from "../ui/format";
import { useTailleRetenue, useValeurDifferee } from "../ui/hooks";
import { IconeScan } from "../ui/icones";
import { Separateur } from "../ui/Separateur";
import { FiltrePrix } from "./FiltrePrix";

type Produit = Schemas["ProduitDto"];

interface Props {
  onAjouter: (produit: Produit) => void;
  onMessage: (message: string) => void;
}

/** Recherche et scan, filtres zone, catégorie et prix, tableau des produits (rupture grisée). */
export function Catalogue({ onAjouter, onMessage }: Props) {
  const champ = useRef<HTMLInputElement>(null);
  const [saisie, setSaisie] = useState("");
  // Une douchette ne se détecte pas au branchement (c'est un clavier) : seulement à son premier scan.
  const [douchette, setDouchette] = useState(false);
  // Curseur dans un autre champ de saisie : le scan y irait, le badge passe en orange.
  const [autreChamp, setAutreChamp] = useState(false);
  const frappes = useRef<number[]>([]);
  const [zoneId, setZoneId] = useState<number | null>(null);
  const [categorieId, setCategorieId] = useState<number | null>(null);
  const recherche = useValeurDifferee(saisie.trim(), 250);
  const zones = useZones();
  const categories = useCategories();
  const bornes = useBornesPrix();
  // Plage de prix appliquée depuis le panneau du bouton « Prix ».
  const [prix, setPrix] = useState<{ bas: number | null; haut: number | null }>({ bas: null, haut: null });
  const [tri, setTri] = useState<TriCatalogue | null>(null);
  const produits = useProduits({
    recherche,
    zoneId,
    categorieId,
    prixMin: prix.bas,
    prixMax: prix.haut,
    tri,
  });
  // Le prix d'achat (PPH) n'est envoyé par l'API qu'à l'admin.
  const voirPph = useUtilisateur().role === "ADMIN";

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
  const pph = useTailleRetenue("invento-pos.colonne-pph", 110);
  const bornesCurseur = bornes.data && {
    min: Math.floor(bornes.data.min),
    max: Math.ceil(bornes.data.max),
  };

  function ajouter(produit: Produit) {
    if (produit.enRupture) {
      onMessage(`Rupture : ${produit.designation}`);
      return;
    }
    onAjouter(produit);
  }

  /** Code tapé puis Entrée : produit ajouté au ticket, sinon le texte reste une recherche dans la grille. */
  async function traiterCode(code: string) {
    try {
      ajouter(await chercherParCodeBarre(code));
      setSaisie("");
    } catch (erreur) {
      if (!(erreur instanceof ErreurApi && erreur.statut === 404)) onMessage(messageErreur(erreur));
      else if (/^\d{6,}$/.test(code)) onMessage(`Aucun produit pour le code-barre ${code}`);
    }
    champ.current?.focus();
  }

  // La douchette tape le code puis Entrée : recherche immédiate, sans délai.
  function valider(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key.length === 1) frappes.current.push(e.timeStamp);
    if (e.key !== "Enter") return;
    e.preventDefault();
    if (estUnScan(frappes.current)) setDouchette(true);
    frappes.current = [];
    const code = saisie.trim();
    if (code) void traiterCode(code);
  }

  // Dernière version de traiterCode pour l'écoute globale, enregistrée une seule fois.
  const traiterCodeRef = useRef(traiterCode);
  useEffect(() => {
    traiterCodeRef.current = traiterCode;
  });

  // Scan quand le curseur n'est dans aucun champ (après un clic sur un produit, le ticket, un bouton) :
  // il est capté ici. Dans un autre champ de saisie ou une fenêtre, on n'intercepte rien.
  useEffect(() => {
    let tampon: { touche: string; instant: number }[] = [];
    const cibleLibre = (cible: EventTarget | null) =>
      cible !== champ.current &&
      !estChampDeSaisie(cible) &&
      !(cible instanceof Element && cible.closest("[role=dialog], dialog"));
    const surTouche = (e: globalThis.KeyboardEvent) => {
      if (!cibleLibre(e.target) || e.ctrlKey || e.altKey || e.metaKey) {
        tampon = [];
        return;
      }
      const dernier = tampon.at(-1);
      if (dernier && e.timeStamp - dernier.instant > 100) tampon = [];
      if (e.key.length === 1) {
        tampon.push({ touche: e.key, instant: e.timeStamp });
        return;
      }
      if (e.key !== "Enter") return;
      const code = tampon
        .map((f) => f.touche)
        .join("")
        .trim();
      const scan = estUnScan(tampon.map((f) => f.instant));
      tampon = [];
      if (!scan || !code) return;
      // Entrée de la douchette : ne pas déclencher le bouton qui a le focus.
      e.preventDefault();
      e.stopPropagation();
      setDouchette(true);
      void traiterCodeRef.current(code);
    };
    const surFocus = (e: FocusEvent) => {
      const cible = e.type === "focusin" ? e.target : e.relatedTarget;
      setAutreChamp(cible !== champ.current && estChampDeSaisie(cible));
    };
    document.addEventListener("keydown", surTouche, true);
    document.addEventListener("focusin", surFocus);
    document.addEventListener("focusout", surFocus);
    return () => {
      document.removeEventListener("keydown", surTouche, true);
      document.removeEventListener("focusin", surFocus);
      document.removeEventListener("focusout", surFocus);
    };
  }, []);

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
            onBlur={() => {
              frappes.current = [];
            }}
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
          {/* Dans le label : un clic sur le badge remet le curseur dans le champ. */}
          <span
            className={`badge-douchette${autreChamp ? " inactif" : douchette ? " detectee" : ""}`}
            aria-live="polite"
          >
            {autreChamp ? "Cliquez ici pour scanner" : douchette ? "Douchette détectée" : "Prêt à scanner"}
          </span>
        </label>
        <Filtre libelle="Zone" options={zones.data ?? []} valeur={zoneId} onChoisir={setZoneId} />
        <Filtre
          libelle="Catégorie"
          options={categories.data ?? []}
          valeur={categorieId}
          onChoisir={setCategorieId}
        />
        {bornesCurseur && bornesCurseur.max > bornesCurseur.min && (
          <FiltrePrix
            min={bornesCurseur.min}
            max={bornesCurseur.max}
            bas={prix.bas}
            haut={prix.haut}
            onChanger={(bas, haut) => setPrix({ bas, haut })}
          />
        )}
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
            "--colonnes": `${designation[0]}px ${zone[0]}px ${categorie[0]}px ${stock[0]}px ${voirPph ? `${pph[0]}px ` : ""}minmax(110px, 1fr)`,
          } as CSSProperties
        }
      >
        <div className="tableau-entete">
          <EnteteColonne libelle="Désignation" taille={designation} min={160} tri={etatTri("designation")} />
          <EnteteColonne libelle="Zone" taille={zone} min={80} tri={etatTri("zone")} />
          <EnteteColonne libelle="Catégorie" taille={categorie} min={80} tri={etatTri("categorie")} />
          <EnteteColonne libelle="Stock" taille={stock} min={72} aDroite tri={etatTri("stock")} />
          {voirPph && <EnteteColonne libelle="PPH" taille={pph} min={80} aDroite />}
          {/* Dernière colonne : elle occupe la place restante, sans poignée. */}
          <span className="entete-colonne a-droite" aria-sort={ariaTri(etatTri("ppv").ordre)}>
            <BoutonTri libelle="Prix TTC" tri={etatTri("ppv")} />
          </span>
        </div>
        {produits.isSuccess && liste.length === 0 && <p className="vide">Aucun produit ne correspond.</p>}
        {liste.map((p) => (
          <LigneProduit key={p.id} produit={p} voirPph={voirPph} onChoisir={() => ajouter(p)} />
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

/**
 * Saisie d'une douchette : au moins 4 caractères tapés en moyenne à moins de 35 ms d'intervalle
 * (une personne au clavier tape rarement plus vite que 80 ms).
 */
function estUnScan(instants: number[]): boolean {
  if (instants.length < 4) return false;
  const premier = instants[0] ?? 0;
  const dernier = instants.at(-1) ?? 0;
  return (dernier - premier) / (instants.length - 1) < 35;
}

/** Champ où une frappe s'écrit (zone de texte, liste, contenu éditable) : un scan y est laissé tel quel. */
function estChampDeSaisie(cible: EventTarget | null): boolean {
  if (!(cible instanceof HTMLElement)) return false;
  if (cible.isContentEditable || cible instanceof HTMLTextAreaElement || cible instanceof HTMLSelectElement) {
    return true;
  }
  return (
    cible instanceof HTMLInputElement &&
    !["button", "checkbox", "radio", "submit", "reset", "range", "file", "color"].includes(cible.type)
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
function LigneProduit({
  produit,
  voirPph,
  onChoisir,
}: {
  produit: Produit;
  voirPph: boolean;
  onChoisir: () => void;
}) {
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
      aria-label={`${produit.designation}, ${produit.zone.nom}, ${produit.categorie.nom}, ${produit.enRupture ? "rupture" : `stock ${stock}`}, ${voirPph && produit.pph != null ? `PPH ${dh(produit.pph)}, ` : ""}${dh(produit.ppv)}`}
      disabled={produit.enRupture}
      onClick={onChoisir}
    >
      <span className="ligne-produit-nom">{produit.designation}</span>
      <span className="ligne-produit-zone">{produit.zone.nom}</span>
      <span className="ligne-produit-zone">{produit.categorie.nom}</span>
      <span className={`stock a-droite ${classeStock}`}>{produit.enRupture ? "Rupture" : stock}</span>
      {voirPph && (
        <span className="ligne-produit-pph a-droite">{produit.pph == null ? "—" : dh(produit.pph)}</span>
      )}
      <span className="ligne-produit-prix a-droite">{dh(produit.ppv)}</span>
    </button>
  );
}
