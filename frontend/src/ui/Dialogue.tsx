import { useEffect, useRef, type ReactNode } from "react";

interface Props {
  titre: string;
  /** Ligne de contexte sous le titre (ticket, produit, caisse). */
  sousTitre?: string;
  /** Conservés pour les appelants ; la fenêtre classique n'affiche plus d'icône. */
  icone?: ReactNode;
  ton?: "info" | "danger" | "succes";
  onFermer: () => void;
  children: ReactNode;
  largeur?: number;
  /** Classe ajoutée à la popup, pour un réglage propre à un écran. */
  classe?: string;
}

/**
 * Popup « fenêtre classique » : titre à gauche avec bouton de fermeture, contenu, puis la rangée de
 * boutons en pied gris (dernier enfant). Échap ferme, le focus reste dans la popup.
 */
export function Dialogue({ titre, sousTitre, onFermer, children, largeur = 460, classe }: Props) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialogue = ref.current;
    if (dialogue && !dialogue.open) dialogue.showModal?.();
  }, []);

  return (
    <dialog
      ref={ref}
      className={classe ? `dialogue ${classe}` : "dialogue"}
      aria-label={titre}
      style={{ width: `min(${largeur}px, calc(100vw - 32px))` }}
      onCancel={(e) => {
        e.preventDefault();
        onFermer();
      }}
    >
      <div className="dialogue-tete">
        <div>
          <h2 className="titre-popup">{titre}</h2>
          {sousTitre && <p className="dialogue-sous-titre">{sousTitre}</p>}
        </div>
        <button type="button" className="dialogue-fermer" aria-label="Fermer" onClick={onFermer}>
          ×
        </button>
      </div>
      <div className="dialogue-corps">{children}</div>
    </dialog>
  );
}
