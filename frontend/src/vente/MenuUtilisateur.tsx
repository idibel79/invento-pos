import { useEffect, useRef, useState } from "react";
import { useAuth, useUtilisateur } from "../auth/AuthContext";
import { LIBELLES_ROLE } from "../auth/roles";
import { pourcent } from "../ui/format";

interface Props {
  /** Sur la caisse : la remise que le vendeur peut accorder est rappelée sous son nom. */
  enCaisse?: boolean;
}

/** Nom et rôle de l'utilisateur dans la barre de caisse ; la déconnexion est dans le menu déroulant. */
export function MenuUtilisateur({ enCaisse = false }: Props) {
  const utilisateur = useUtilisateur();
  const { deconnecter } = useAuth();
  const [ouvert, setOuvert] = useState(false);
  const conteneur = useRef<HTMLDivElement>(null);
  const declencheur = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!ouvert) return;
    const clic = (e: PointerEvent) => {
      if (!conteneur.current?.contains(e.target as Node)) setOuvert(false);
    };
    const touche = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      setOuvert(false);
      declencheur.current?.focus();
    };
    document.addEventListener("pointerdown", clic);
    document.addEventListener("keydown", touche);
    return () => {
      document.removeEventListener("pointerdown", clic);
      document.removeEventListener("keydown", touche);
    };
  }, [ouvert]);

  return (
    <div className="barre-utilisateur" ref={conteneur}>
      <button
        ref={declencheur}
        type="button"
        className="barre-utilisateur-bouton"
        aria-haspopup="menu"
        aria-expanded={ouvert}
        onClick={() => setOuvert((o) => !o)}
      >
        <span className="avatar" aria-hidden="true">
          {utilisateur.prenom.charAt(0)}
          {utilisateur.nom.charAt(0)}
        </span>
        <span className="barre-utilisateur-texte">
          <span className="utilisateur-nom">
            {utilisateur.prenom} {utilisateur.nom}
          </span>
          <span className="utilisateur-role">
            {LIBELLES_ROLE[utilisateur.role]}
            {enCaisse && utilisateur.role === "CAISSIER" && " · sans remise"}
            {enCaisse &&
              utilisateur.role === "RESPONSABLE" &&
              ` · remise max ${pourcent(utilisateur.seuilsRemise.ligne)} ligne, ${pourcent(utilisateur.seuilsRemise.ticket)} ticket`}
          </span>
        </span>
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          aria-hidden="true"
        >
          <path d="M6 9l6 6 6-6" strokeLinecap="round" />
        </svg>
      </button>
      {ouvert && (
        <div className="menu-deroulant" role="menu" aria-label="Menu utilisateur">
          <button
            type="button"
            role="menuitem"
            className="menu-element danger"
            autoFocus
            onClick={() => void deconnecter()}
          >
            Déconnexion
          </button>
        </div>
      )}
    </div>
  );
}
