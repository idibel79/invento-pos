import { useState } from "react";
import type { Schemas } from "../api/client";
import { messageErreur } from "../api/erreurs";
import { useParametres } from "../caisse/requetes";
import { dateHeure } from "../ui/format";
import { IconeAlerte } from "../ui/icones";
import { useAnalyserImport, useImporterCatalogue } from "./requetes";

type Rapport = Schemas["RapportImport"];

const FORMATS = {
  SOBRUS: "Export Sobrus (stock théorique)",
  INVENTO: "Export Invento (quantités comptées à l'inventaire)",
} as const;

/** Import Excel du catalogue : analyse sans écriture, choix de l'inventaire, puis import. */
export function ImportCatalogue() {
  const fuseau = useParametres().data?.fuseauHoraire;
  const analyser = useAnalyserImport();
  const importer = useImporterCatalogue();
  const [fichier, setFichier] = useState<File | null>(null);
  // Dernière analyse réussie, gardée affichée pendant qu'on réanalyse un autre inventaire.
  const [analyse, setAnalyse] = useState<Rapport | null>(null);
  const inventaire = analyse?.inventaireRetenu?.numero ?? null;

  const analyserFichier = (choisi: File, numero: string | null) =>
    analyser.mutate({ fichier: choisi, inventaire: numero }, { onSuccess: setAnalyse });

  const choisirFichier = (choisi: File | null) => {
    setFichier(choisi);
    setAnalyse(null);
    importer.reset();
    if (choisi) analyserFichier(choisi, null);
  };

  return (
    <section className="admin-section">
      <div className="admin-tete">
        <div>
          <h1 className="admin-titre">Import du catalogue</h1>
          <p className="texte-secondaire">
            Fichier Excel (.xlsx) exporté de Sobrus (« Stocks ») ou d'Invento. Le fichier est d'abord analysé,
            rien n'est enregistré avant de cliquer sur Importer.
          </p>
        </div>
      </div>
      <div className="carte admin-carte">
        <label className="champ">
          <span className="libelle">Fichier</span>
          <input
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            disabled={importer.isPending}
            onChange={(e) => choisirFichier(e.target.files?.[0] ?? null)}
          />
        </label>
        {analyser.isPending && <p className="texte-secondaire">Analyse du fichier…</p>}
        {analyser.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(analyser.error)}
          </p>
        )}
        {analyse && fichier && !importer.isSuccess && (
          <>
            <p className="encadre-ligne">
              <span>Format détecté</span>
              <span>{FORMATS[analyse.format]}</span>
            </p>
            {analyse.inventaires.length > 0 && (
              <label className="champ">
                <span className="libelle">Inventaire à importer</span>
                <select
                  value={inventaire ?? ""}
                  disabled={analyser.isPending || importer.isPending}
                  onChange={(e) => analyserFichier(fichier, e.target.value)}
                >
                  {analyse.inventaires.map((i) => (
                    <option key={i.numero} value={i.numero}>
                      {i.numero} · {dateHeure(i.date, fuseau)} · {i.nombreArticles} articles
                    </option>
                  ))}
                </select>
              </label>
            )}
            <Compteurs rapport={analyse} />
            <Messages rapport={analyse} />
            <div className="alerte alerte-attention">
              <IconeAlerte />
              <span>
                <strong>Le stock sera remplacé</strong>
                Chaque produit du fichier prend la quantité du fichier ; l'écart est tracé en mouvement de
                stock.
                {analyse.nombreErreurs > 0 &&
                  ` Les ${analyse.nombreErreurs} lignes en erreur seront ignorées.`}
              </span>
            </div>
            {importer.isError && (
              <p className="alerte alerte-danger" role="alert">
                {messageErreur(importer.error)}
              </p>
            )}
            <div className="rangee-boutons">
              <button
                type="button"
                className="bouton bouton-plein"
                disabled={importer.isPending || analyser.isPending || analyse.lignesLues === 0}
                onClick={() => importer.mutate({ fichier, inventaire })}
              >
                {importer.isPending ? "Import en cours…" : "Importer"}
              </button>
            </div>
          </>
        )}
        {importer.data && (
          <>
            <p className="alerte alerte-succes" role="status">
              Import terminé : {importer.data.produitsCrees} produits créés, {importer.data.produitsMisAJour}{" "}
              mis à jour, {importer.data.mouvementsStock} mouvements de stock.
            </p>
            <Compteurs rapport={importer.data} />
            <Messages rapport={importer.data} />
          </>
        )}
      </div>
    </section>
  );
}

function Compteurs({ rapport }: { rapport: Rapport }) {
  const lignes: [string, number][] = [
    ["Lignes lues", rapport.lignesLues],
    ["Sans code-barres", rapport.produitsSansCodeBarre],
    ["Stock négatif", rapport.produitsEnStockNegatif],
    ["Erreurs", rapport.nombreErreurs],
    ["Avertissements", rapport.nombreAvertissements],
  ];
  return (
    <div className="encadre-gris">
      {lignes.map(([libelle, valeur]) => (
        <div key={libelle} className="encadre-ligne">
          <span>{libelle}</span>
          <span>{valeur}</span>
        </div>
      ))}
    </div>
  );
}

/** Messages bornés côté serveur (100 au plus), les compteurs restent exacts. */
function Messages({ rapport }: { rapport: Rapport }) {
  const groupes = [
    { titre: "Erreurs", total: rapport.nombreErreurs, messages: rapport.erreurs },
    { titre: "Avertissements", total: rapport.nombreAvertissements, messages: rapport.avertissements },
  ].filter((g) => g.messages.length > 0);
  return groupes.map((g) => (
    <details key={g.titre} className="details-import">
      <summary>
        {g.titre} ({g.total})
      </summary>
      <ul>
        {g.messages.map((m, i) => (
          <li key={i}>{m}</li>
        ))}
      </ul>
      {g.total > g.messages.length && (
        <p className="texte-secondaire">… et {g.total - g.messages.length} autres.</p>
      )}
    </details>
  ));
}
