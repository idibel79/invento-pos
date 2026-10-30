import { useEffect, useRef, type ReactNode } from "react";

interface Props {
  titre: string;
  icone?: ReactNode;
  /** Teinte du rond de l'icône. */
  ton?: "info" | "danger" | "succes";
  onFermer: () => void;
  children: ReactNode;
  largeur?: number;
}

/**
 * Popup de la charte Invento : voile marine à 42 %, carte arrondie 24 px, icône dans un rond
 * teinté, titre centré. Échap ferme, le focus reste dans la popup.
 */
export function Dialogue({ titre, icone, ton = "info", onFermer, children, largeur = 520 }: Props) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialogue = ref.current;
    if (dialogue && !dialogue.open) dialogue.showModal?.();
  }, []);

  return (
    <dialog
      ref={ref}
      className="dialogue"
      aria-label={titre}
      style={{ width: `min(${largeur}px, calc(100vw - 32px))` }}
      onCancel={(e) => {
        e.preventDefault();
        onFermer();
      }}
    >
      <div className="dialogue-tete">
        {icone && (
          <span className={`dialogue-icone ton-${ton}`} aria-hidden="true">
            {icone}
          </span>
        )}
        <h2 className="titre-popup">{titre}</h2>
      </div>
      {children}
    </dialog>
  );
}
