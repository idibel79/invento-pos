/**
 * Échelle d'affichage : l'interface garde la même densité sur tous les écrans de poste de caisse.
 * Référence : 1536 px de large (1920 × 1080 sous Windows à 125 %) avec Chrome zoomé à 90 %,
 * soit environ 1707 px de mise en page. De 1024 px de fenêtre (échelle 0,6) jusqu'à l'échelle 2,5
 * (écran 4K), la page est mise à l'échelle et le zoom de Chrome est compensé. Sous 1024 px
 * (tablette, ou Chrome zoomé au-delà d'environ 150 %), l'échelle revient à 1 : la mise en page
 * étroite de styles.css prend le relais et Ctrl+ grossit vraiment, pour la lisibilité.
 */
const LARGEUR_REFERENCE = 1536 / 0.9;
const LARGEUR_MIN_ECHELLE = 1024;
const ECHELLE_MAX = 2.5;

let echelleCourante = 1;

export function calculerEchelle(largeurFenetre: number): number {
  if (largeurFenetre < LARGEUR_MIN_ECHELLE) return 1;
  const echelle = Math.min(ECHELLE_MAX, largeurFenetre / LARGEUR_REFERENCE);
  return Math.round(echelle * 1000) / 1000;
}

/** Échelle appliquée à la page (1 tant qu'elle n'est pas installée, par exemple en test). */
export function echelle(): number {
  return echelleCourante;
}

/** Pose l'échelle sur la racine (variable CSS `--echelle`) et la suit au redimensionnement. */
export function installerEchelle(): void {
  const appliquer = () => {
    echelleCourante = calculerEchelle(window.innerWidth);
    document.documentElement.style.setProperty("--echelle", String(echelleCourante));
  };
  appliquer();
  window.addEventListener("resize", appliquer);
}
