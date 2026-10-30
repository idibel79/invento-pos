export function Logo() {
  return (
    <div className="logo">
      <span className="logo-icone" aria-hidden="true">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M3 7h18l-2 11H5z" strokeLinejoin="round" />
          <path d="M8 7V5a4 4 0 0 1 8 0v2" strokeLinecap="round" />
        </svg>
      </span>
      <span className="logo-texte">Invento POS</span>
    </div>
  );
}
