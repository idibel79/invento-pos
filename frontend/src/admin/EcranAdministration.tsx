import { useState } from "react";
import { useUtilisateur } from "../auth/AuthContext";
import { useParametres } from "../caisse/requetes";
import { Clients } from "../client/Clients";
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

/** Onglets par métier : ce qui a été vendu, qui doit de l'argent, où il est, les réglages. */
const ONGLETS = {
  ventes: "Ventes",
  clients: "Clients",
  tresorerie: "Trésorerie",
  parametres: "Paramètres",
} as const;
type Onglet = keyof typeof ONGLETS;

/** L'admin a tous les onglets ; les responsables et caissiers leurs ventes, les clients et leur compte. */
const ONGLETS_ADMIN: Onglet[] = ["ventes", "clients", "tresorerie", "parametres"];
const ONGLETS_VENDEUR: Onglet[] = ["ventes", "clients", "tresorerie"];

const VENTES: EntreeMenu[] = [
  { nom: "Journal des ventes", detail: "Ventes validées, jour par jour", ecran: () => <HistoriqueVentes /> },
  { nom: "Remises", detail: "Remises accordées sur les ventes", ecran: () => <JournalRemises /> },
];

const ANNUAIRE: EntreeMenu = {
  nom: "Annuaire",
  detail: "Fiches des clients",
  ecran: () => <Clients />,
};
const DETTES: EntreeMenu = {
  nom: "Crédits et dettes",
  detail: "Qui doit combien, encaisser",
  ecran: () => <Clients vue="dettes" />,
};
const VENTES_CLIENTS: EntreeMenu = {
  nom: "Ventes et factures",
  detail: "Factures émises, réimpression",
  ecran: () => <Clients vue="ventes" />,
};
const CLIENTS: EntreeMenu[] = [ANNUAIRE, DETTES, VENTES_CLIENTS];

/** Trésorerie d'un responsable ou d'un caissier : son seul compte (l'admin voit tous les comptes). */
const TRESORERIE_VENDEUR: EntreeMenu[] = [
  { nom: "Mon compte", detail: "Solde dû, versements et ventes", ecran: () => <MonCompte /> },
];

const PARAMETRES: EntreeMenu[] = [
  { nom: "Boutique", detail: "Nom, adresse, contact du ticket", ecran: () => <Boutique /> },
  { nom: "Caisses", detail: "Créer, renommer, espèces", ecran: () => <Caisses /> },
  { nom: "Utilisateurs", detail: "Rôles, accès, seuils de remise", ecran: () => <Utilisateurs /> },
  {
    nom: "Import du catalogue de test",
    detail: "Fichier Excel des produits",
    ecran: () => <ImportCatalogue />,
  },
  { nom: "Sécurité", detail: "Code de secours de l'admin", ecran: () => <CodeSecours /> },
];

/** Administration ; le serveur vérifie le rôle sur chaque appel et filtre les journaux hors admin. */
export function EcranAdministration({ onQuitter }: { onQuitter: () => void }) {
  const admin = useUtilisateur().role === "ADMIN";
  const onglets = admin ? ONGLETS_ADMIN : ONGLETS_VENDEUR;
  // Un vendeur arrive sur son compte, l'admin sur les ventes.
  const [onglet, setOnglet] = useState<Onglet>(admin ? "ventes" : "tresorerie");
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
            {cle === "tresorerie" && aValider > 0 && (
              <span className="pastille-nombre" aria-label={`${aValider} à valider`}>
                {aValider}
              </span>
            )}
          </button>
        ))}
      </nav>
      <main className="admin-corps">
        {onglet === "ventes" && <RubriqueAvecMenu titre="Ventes" entrees={VENTES} />}
        {onglet === "clients" && <RubriqueAvecMenu titre="Clients" entrees={CLIENTS} />}
        {onglet === "tresorerie" &&
          (admin ? (
            <CaissePrincipale />
          ) : (
            <RubriqueAvecMenu titre="Trésorerie" entrees={TRESORERIE_VENDEUR} />
          ))}
        {onglet === "parametres" && <RubriqueAvecMenu titre="Paramètres" entrees={PARAMETRES} />}
      </main>
    </div>
  );
}
