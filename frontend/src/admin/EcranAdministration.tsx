import { useState } from "react";
import { useParametres } from "../caisse/requetes";
import { heure } from "../ui/format";
import { useMaintenant } from "../ui/hooks";
import { IconeInvento } from "../ui/Logo";
import { MenuUtilisateur } from "../vente/MenuUtilisateur";
import { CaissePrincipale } from "./CaissePrincipale";
import { Caisses } from "./Caisses";
import { CodeSecours } from "./CodeSecours";
import { ImportCatalogue } from "./ImportCatalogue";
import { JournalRemises } from "./JournalRemises";
import { Utilisateurs } from "./Utilisateurs";

const ONGLETS = {
  caisses: "Caisses",
  utilisateurs: "Utilisateurs",
  tresorerie: "Caisse principale",
  import: "Import du catalogue",
  securite: "Code de secours",
  journal: "Journal",
} as const;
type Onglet = keyof typeof ONGLETS;

/** Écrans réservés à l'admin ; le serveur vérifie le rôle sur chaque appel. */
export function EcranAdministration({ onQuitter }: { onQuitter: () => void }) {
  const [onglet, setOnglet] = useState<Onglet>("caisses");
  const fuseau = useParametres().data?.fuseauHoraire;
  const maintenant = useMaintenant(30_000);

  return (
    <div className="ecran-admin">
      <header className="barre-haut">
        <span className="barre-marque">
          <IconeInvento />
          Invento POS
        </span>
        <span className="barre-session">
          <strong>Administration</strong>
          <span>réservée à l'admin</span>
        </span>
        <span className="barre-espace" />
        <button type="button" className="barre-bouton" onClick={onQuitter}>
          Retour à la caisse
        </button>
        <span className="barre-heure">{heure(new Date(maintenant).toISOString(), fuseau)}</span>
        <MenuUtilisateur />
      </header>
      <nav className="admin-onglets" aria-label="Administration">
        {(Object.keys(ONGLETS) as Onglet[]).map((cle) => (
          <button
            key={cle}
            type="button"
            className="admin-onglet"
            aria-current={onglet === cle ? "page" : undefined}
            onClick={() => setOnglet(cle)}
          >
            {ONGLETS[cle]}
          </button>
        ))}
      </nav>
      <main className="admin-corps">
        {onglet === "caisses" && <Caisses />}
        {onglet === "utilisateurs" && <Utilisateurs />}
        {onglet === "tresorerie" && <CaissePrincipale />}
        {onglet === "import" && <ImportCatalogue />}
        {onglet === "securite" && <CodeSecours />}
        {onglet === "journal" && <JournalRemises />}
      </main>
    </div>
  );
}
