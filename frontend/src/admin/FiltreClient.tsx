import { useState } from "react";
import { useValeurDifferee } from "../ui/hooks";

/**
 * Recherche par client des journaux : la saisie, et la partie du nom envoyée au serveur une fois la frappe
 * terminée (absente si le champ est vide).
 */
export function useRechercheClient(): [string, (saisie: string) => void, string | undefined] {
  const [saisie, setSaisie] = useState("");
  const client = useValeurDifferee(saisie.trim(), 300);
  return [saisie, setSaisie, client || undefined];
}

export function FiltreClient({ valeur, onChange }: { valeur: string; onChange: (saisie: string) => void }) {
  return (
    <label className="filtre filtre-client">
      <span>Client :</span>
      <input
        type="search"
        value={valeur}
        maxLength={100}
        placeholder="Nom du client"
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  );
}
