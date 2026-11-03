/** Icône de l'app Invento (le « i » code-barres de l'icône Android), sur sa tuile bleu marine. */
export function IconeInvento() {
  return (
    <span className="logo-icone" aria-hidden="true">
      <svg width="24" height="24" viewBox="0 0 64 64" fill="none">
        <circle cx="19" cy="18.5" r="3.4" className="logo-accent" />
        <rect x="16.5" y="25" width="5" height="22" rx="1" className="logo-trait" />
        <rect x="25" y="17" width="2.5" height="30" rx="1" className="logo-trait" />
        <rect x="30.5" y="17" width="5.5" height="30" rx="1" className="logo-trait" />
        <rect x="39" y="17" width="2.5" height="30" rx="1" className="logo-trait" />
        <rect x="44.5" y="17" width="4" height="30" rx="1" className="logo-trait" />
        <rect x="12" y="31" width="40" height="2.6" rx="1.3" className="logo-accent" />
      </svg>
    </span>
  );
}

export function Logo() {
  return (
    <div className="logo">
      <IconeInvento />
      <span className="logo-texte">Invento POS</span>
    </div>
  );
}
