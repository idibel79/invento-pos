import { useEffect, useRef, useState } from "react";
import { useAuth, useUtilisateur } from "../auth/AuthContext";
import { LIBELLES_ROLE } from "../auth/roles";
import { useMonCompte } from "../tresorerie/requetes";
import { pourcent } from "../ui/format";

interface Props {
  /** Sur la caisse : la remise que le vendeur peut accorder est rappelée sous son nom. */
  enCaisse?: boolean;
  /** Ajoute l'entrée « Administration » au menu, avec les versements à valider signalés sur l'avatar. */
  onAdministration?: () => void;
}

/**
 * Nom et rôle de l'utilisateur dans la barre du haut ; l'administration et la déconnexion sont dans le
 * menu déroulant.
 */
export function MenuUtilisateur({ enCaisse = false, onAdministration }: Props) {
  const utilisateur = useUtilisateur();
  const { deconnecter } = useAuth();
  const aValider = useMonCompte().data?.aValider.length ?? 0;
  const signale = onAdministration !== undefined && aValider > 0;
  const versements = `${aValider} versement${aValider > 1 ? "s" : ""} à valider`;
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
        aria-label={`${utilisateur.prenom} ${utilisateur.nom}, menu du compte${signale ? `, ${versements}` : ""}`}
        onClick={() => setOuvert((o) => !o)}
      >
        <span className="avatar" aria-hidden="true">
          {utilisateur.prenom.charAt(0)}
          {utilisateur.nom.charAt(0)}
          {signale && <span className="avatar-pastille">{aValider}</span>}
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
          {onAdministration && (
            <>
              <button
                type="button"
                role="menuitem"
                className="menu-element menu-element-riche"
                autoFocus
                aria-label={`Administration${signale ? `, ${versements}` : ""}`}
                onClick={() => {
                  setOuvert(false);
                  onAdministration();
                }}
              >
                <svg
                  width="20"
                  height="20"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <circle cx="12" cy="12" r="3" />
                  <path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1" />
                </svg>
                <span className="menu-element-texte">
                  <span>Administration</span>
                  <span className="menu-element-detail">
                    {utilisateur.role === "ADMIN"
                      ? "Ventes, clients, trésorerie, paramètres"
                      : "Mon compte, ventes, clients"}
                  </span>
                </span>
                {signale && <span className="pastille-nombre">{aValider}</span>}
              </button>
              <span className="menu-separateur" role="separator" />
            </>
          )}
          <button
            type="button"
            role="menuitem"
            className="menu-element menu-element-riche danger"
            autoFocus={!onAdministration}
            onClick={() => void deconnecter()}
          >
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <path d="M16 17l5-5-5-5" />
              <path d="M21 12H9" />
            </svg>
            Déconnexion
          </button>
        </div>
      )}
    </div>
  );
}
