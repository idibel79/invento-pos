import { describe, expect, it } from "vitest";
import { decalerMois, grilleMois, libelleJour, libelleMois } from "./calendrier";

describe("calendrier", () => {
  it("commence la grille le lundi et la complète en semaines", () => {
    const grille = grilleMois("2026-11");
    // Le 1er novembre 2026 est un dimanche : six jours d'octobre avant lui.
    expect(grille[0]).toEqual({ date: "2026-10-26", numero: 26, dansLeMois: false });
    expect(grille[6]).toEqual({ date: "2026-11-01", numero: 1, dansLeMois: true });
    expect(grille.length % 7).toBe(0);
    expect(grille.filter((c) => c.dansLeMois)).toHaveLength(30);
  });

  it("passe d'un mois à l'autre, année comprise", () => {
    expect(decalerMois("2026-12", 1)).toBe("2027-01");
    expect(decalerMois("2026-01", -1)).toBe("2025-12");
  });

  it("libellés en français", () => {
    expect(libelleMois("2026-11")).toBe("Novembre 2026");
    expect(libelleJour("2026-11-04")).toBe("Mercredi 4 novembre");
  });
});
