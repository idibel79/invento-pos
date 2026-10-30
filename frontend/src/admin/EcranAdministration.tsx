import { useState } from "react";
import { useAuth, useUtilisateur } from "../auth/AuthContext";
import { LIBELLES_ROLE } from "../auth/roles";
import { Logo } from "../ui/Logo";
import { Caisses } from "./Caisses";
import { CodeSecours } from "./CodeSecours";
import { ImportCatalogue } from "./ImportCatalogue";
import { Utilisateurs } from "./Utilisateurs";

const ONGLETS = {
  caisses: "Caisses",
  utilisateurs: "Utilisateurs",
  import: "Import du catalogue",
  securite: "Code de secours",
} as const;
type Onglet = keyof typeof ONGLETS;

/** Écrans réservés à l'admin ; le serveur vérifie le rôle sur chaque appel. */
export function EcranAdministration({ onQuitter }: { onQuitter: () => void }) {
  const utilisateur = useUtilisateur();
  const { deconnecter } = useAuth();
  const [onglet, setOnglet] = useState<Onglet>("caisses");

  return (
    <div className="ecran-admin">
      <header className="entete">
        <Logo />
        <span className="pastille">Administration</span>
        <nav className="onglets" aria-label="Administration">
          {(Object.keys(ONGLETS) as Onglet[]).map((cle) => (
            <button
              key={cle}
              type="button"
              className="onglet"
              aria-current={onglet === cle ? "page" : undefined}
              onClick={() => setOnglet(cle)}
            >
              {ONGLETS[cle]}
            </button>
          ))}
        </nav>
        <span className="entete-espace" />
        <button type="button" className="bouton-petit" onClick={onQuitter}>
          Retour à la caisse
        </button>
        <div className="entete-utilisateur">
          <span className="avatar" aria-hidden="true">
            {utilisateur.prenom.charAt(0) || utilisateur.nom.charAt(0)}
          </span>
          <div>
            <div className="utilisateur-nom">
              {utilisateur.prenom} {utilisateur.nom}
            </div>
            <div className="utilisateur-role">{LIBELLES_ROLE[utilisateur.role]}</div>
          </div>
          <button type="button" className="bouton-petit" onClick={() => void deconnecter()}>
            Déconnexion
          </button>
        </div>
      </header>
      <main className="admin-corps">
        {onglet === "caisses" && <Caisses />}
        {onglet === "utilisateurs" && <Utilisateurs />}
        {onglet === "import" && <ImportCatalogue />}
        {onglet === "securite" && <CodeSecours />}
      </main>
    </div>
  );
}
