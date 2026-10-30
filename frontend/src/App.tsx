import { ChangementMotDePasse } from "./auth/ChangementMotDePasse";
import { Connexion } from "./auth/Connexion";
import { useAuth } from "./auth/AuthContext";
import { OuvertureSession } from "./caisse/OuvertureSession";
import { useSessionCourante } from "./caisse/requetes";
import { EcranVente } from "./vente/EcranVente";

export function App() {
  const { statut, utilisateur } = useAuth();
  if (statut === "chargement") return <p className="chargement">Chargement…</p>;
  if (!utilisateur) return <Connexion />;
  if (utilisateur.doitChangerMotDePasse) return <ChangementMotDePasse />;
  return <Caisse />;
}

/** Vente si une session de caisse est ouverte, sinon ouverture de la caisse. */
function Caisse() {
  const session = useSessionCourante();
  if (session.isPending) return <p className="chargement">Chargement de la caisse…</p>;
  if (session.isError) {
    return (
      <p className="chargement" role="alert">
        {session.error.message}
      </p>
    );
  }
  return session.data ? <EcranVente session={session.data} /> : <OuvertureSession />;
}
