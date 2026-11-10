import { useState } from "react";
import { useUtilisateur } from "../auth/AuthContext";
import { useParametres } from "../caisse/requetes";
import { MonCompte } from "../tresorerie/MonCompte";
import { useMonCompte } from "../tresorerie/requetes";
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
  comptes: "Comptes",
  import: "Import du catalogue",
  securite: "Code de secours",
  journaux: "Journaux",
} as const;
type Onglet = keyof typeof ONGLETS;

/** L'admin a toutes les rubriques ; les responsables et caissiers seulement leur compte et leurs journaux. */
const ONGLETS_ADMIN: Onglet[] = ["parametres", "comptes", "import", "securite", "journaux"];
const ONGLETS_VENDEUR: Onglet[] = ["comptes", "journaux"];

/** Comptes d'un responsable ou d'un caissier : son seul compte (l'admin voit tous les comptes). */
const COMPTES_VENDEUR: EntreeMenu[] = [
  { nom: "Mon compte", detail: "Solde dû, versements et ventes", ecran: () => <MonCompte /> },
];

const PARAMETRES: EntreeMenu[] = [
  { nom: "Boutique", detail: "Nom, adresse, contact du ticket", ecran: () => <Boutique /> },
  { nom: "Caisses", detail: "Créer, renommer, espèces", ecran: () => <Caisses /> },
  { nom: "Utilisateurs", detail: "Comptes, rôles, seuils de remise", ecran: () => <Utilisateurs /> },
];

const JOURNAUX: EntreeMenu[] = [
  { nom: "Ventes", detail: "Ventes validées, jour par jour", ecran: () => <HistoriqueVentes /> },
  { nom: "Remises", detail: "Remises accordées sur les ventes", ecran: () => <JournalRemises /> },
];

/** Administration ; le serveur vérifie le rôle sur chaque appel et filtre les journaux hors admin. */
export function EcranAdministration({ onQuitter }: { onQuitter: () => void }) {
  const admin = useUtilisateur().role === "ADMIN";
  const onglets = admin ? ONGLETS_ADMIN : ONGLETS_VENDEUR;
  const [onglet, setOnglet] = useState<Onglet>(onglets[0] ?? "comptes");
  const fuseau = useParametres().data?.fuseauHoraire;
  const maintenant = useMaintenant(30_000);
  const aValider = useMonCompte().data?.aValider.length ?? 0;

  return (
    <div className="ecran-admin">
      <header className="barre-haut">
        <span className="barre-marque">
          <IconeInvento />
          Invento POS
        </span>
        <span className="barre-session">
          <strong>Administration</strong>
          {admin && <span>réservée à l'admin</span>}
        </span>
        <span className="barre-espace" />
        <button type="button" className="barre-bouton" onClick={onQuitter}>
          Retour à la caisse
        </button>
        <span className="barre-heure">{heure(new Date(maintenant).toISOString(), fuseau)}</span>
        <MenuUtilisateur />
      </header>
      <nav className="admin-onglets" aria-label="Administration">
        {onglets.map((cle) => (
          <button
            key={cle}
            type="button"
            className="admin-onglet"
            aria-current={onglet === cle ? "page" : undefined}
            onClick={() => setOnglet(cle)}
          >
            {ONGLETS[cle]}
            {cle === "comptes" && aValider > 0 && (
              <span className="pastille-nombre" aria-label={`${aValider} à valider`}>
                {aValider}
              </span>
            )}
          </button>
        ))}
      </nav>
      <main className="admin-corps">
        {onglet === "parametres" && <RubriqueAvecMenu titre="Paramètres" entrees={PARAMETRES} />}
        {onglet === "comptes" &&
          (admin ? <CaissePrincipale /> : <RubriqueAvecMenu titre="Comptes" entrees={COMPTES_VENDEUR} />)}
        {onglet === "import" && <ImportCatalogue />}
        {onglet === "securite" && <CodeSecours />}
        {onglet === "journaux" && <RubriqueAvecMenu titre="Journaux" entrees={JOURNAUX} />}
      </main>
    </div>
  );
}
