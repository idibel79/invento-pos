import { useState, type KeyboardEvent, type PointerEvent } from "react";

interface Props {
  /** Nom lu par les lecteurs d'écran (« Largeur du ticket »). */
  libelle: string;
  largeur: number;
  min: number;
  max: number;
  /** 1 : tirer vers la droite élargit le panneau (à gauche du séparateur) ; -1 : l'inverse. */
  sens: 1 | -1;
  onLargeur: (largeur: number) => void;
}

const PAS_CLAVIER = 20;

/** Bord vertical à tirer à la souris (ou aux flèches) pour élargir ou réduire un panneau. */
export function Separateur({ libelle, largeur, min, max, sens, onLargeur }: Props) {
  const [depart, setDepart] = useState<{ x: number; largeur: number } | null>(null);
  const borner = (valeur: number) => Math.round(Math.min(max, Math.max(min, valeur)));

  function commencer(e: PointerEvent<HTMLDivElement>) {
    e.currentTarget.setPointerCapture(e.pointerId);
    setDepart({ x: e.clientX, largeur });
  }

  function deplacer(e: PointerEvent<HTMLDivElement>) {
    if (depart) onLargeur(borner(depart.largeur + sens * (e.clientX - depart.x)));
  }

  function clavier(e: KeyboardEvent<HTMLDivElement>) {
    const delta = e.key === "ArrowRight" ? PAS_CLAVIER : e.key === "ArrowLeft" ? -PAS_CLAVIER : 0;
    if (delta === 0) return;
    e.preventDefault();
    onLargeur(borner(largeur + sens * delta));
  }

  return (
    <div
      className={`separateur${depart ? " actif" : ""}`}
      role="separator"
      aria-orientation="vertical"
      aria-label={libelle}
      aria-valuenow={largeur}
      aria-valuemin={min}
      aria-valuemax={max}
      tabIndex={0}
      onPointerDown={commencer}
      onPointerMove={deplacer}
      onPointerUp={() => setDepart(null)}
      onPointerCancel={() => setDepart(null)}
      onKeyDown={clavier}
    />
  );
}
