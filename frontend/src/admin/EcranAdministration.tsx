import { useState } from "react";
import { useParametres } from "../caisse/requetes";
import { heure } from "../ui/format";
import { useMaintenant } from "../ui/hooks";
import { IconeInvento } from "../ui/Logo";
import { MenuUtilisateur } from "../vente/MenuUtilisateur";
import { Boutique } from "./Boutique";
import { CaissePrincipale } from "./CaissePrincipale";
import { Caisses } from "./Caisses";
import { CodeSecours } from "./CodeSecours";
import { HistoriqueVentes } from "./HistoriqueVentes";
import { ImportCatalogue } from "./ImportCatalogue";
import { JournalRemises } from "./JournalRemises";
import { type EntreeMenu, RubriqueAvecMenu } from "./RubriqueAvecMenu";
import { Utilisateurs } from "./Utilisateurs";

const ONGLETS = {
  parametres: "Paramètres",
  tresorerie: "Caisse principale",
  import: "Import du catalogue",
  securite: "Code de secours",
  journaux: "Journaux",
} as const;
type Onglet = keyof typeof ONGLETS;

const PARAMETRES: EntreeMenu[] = [
  { nom: "Boutique", detail: "Nom, adresse, contact du ticket", ecran: () => <Boutique /> },
  { nom: "Caisses", detail: "Créer, renommer, espèces", ecran: () => <Caisses /> },
  { nom: "Utilisateurs", detail: "Comptes, rôles, seuils de remise", ecran: () => <Utilisateurs /> },
];

const JOURNAUX: EntreeMenu[] = [
  { nom: "Ventes", detail: "Ventes validées, jour par jour", ecran: () => <HistoriqueVentes /> },
  { nom: "Remises", detail: "Remises accordées sur les ventes", ecran: () => <JournalRemises /> },
];

/** Écrans réservés à l'admin ; le serveur vérifie le rôle sur chaque appel. */
export function EcranAdministration({ onQuitter }: { onQuitter: () => void }) {
  const [onglet, setOnglet] = useState<Onglet>("parametres");
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
        {onglet === "parametres" && <RubriqueAvecMenu titre="Paramètres" entrees={PARAMETRES} />}
        {onglet === "tresorerie" && <CaissePrincipale />}
        {onglet === "import" && <ImportCatalogue />}
        {onglet === "securite" && <CodeSecours />}
        {onglet === "journaux" && <RubriqueAvecMenu titre="Journaux" entrees={JOURNAUX} />}
      </main>
    </div>
  );
}
