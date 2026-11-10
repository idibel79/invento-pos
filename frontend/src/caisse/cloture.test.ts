import { describe, expect, it } from "vitest";
import { apercuCloture, ecartsParMode, lireComptage } from "./cloture";

describe("lireComptage", () => {
  it("additionne espèces et tickets TPE, un champ vide valant 0", () => {
    expect(lireComptage("100,50", "")).toEqual({ especes: 100.5, tpe: 0, total: 100.5 });
    expect(lireComptage("100,10", "50,20")).toEqual({ especes: 100.1, tpe: 50.2, total: 150.3 });
  });

  it("rien tant que les deux champs sont vides ou qu'une saisie est invalide", () => {
    expect(lireComptage("", " ")).toBeNull();
    expect(lireComptage("12", "abc")).toBeNull();
  });
});

describe("ecartsParMode", () => {
  it("compare chaque mode à son attendu", () => {
    expect(ecartsParMode(660.6, 100, { especes: 657, tpe: 98, total: 755 })).toEqual({
      especes: -3.6,
      tpe: -2,
    });
    expect(ecartsParMode(660.6, 100, null)).toBeNull();
  });
});

describe("apercuCloture", () => {
  it("un manque ne réduit pas le solde dû", () => {
    const apercu = apercuCloture(1500, 1500, 1480, 1480);
    expect(apercu.ecart).toBe(-20);
    expect(apercu.resteDu).toBe(20);
    expect(apercu.resteAvecVous).toBe(0);
    expect(apercu.verseValide).toBe(true);
  });

  it("un excédent s'ajoute au solde dû", () => {
    const apercu = apercuCloture(1500, 1500, 1510, 1000);
    expect(apercu.ecart).toBe(10);
    expect(apercu.resteDu).toBe(510);
    expect(apercu.resteAvecVous).toBe(510);
  });

  it("le versé est compris entre 0 et le compté", () => {
    expect(apercuCloture(100, 100, 100, 101).verseValide).toBe(false);
    expect(apercuCloture(100, 100, null, 0).verseValide).toBe(false);
    expect(apercuCloture(100, 100, 100, 0).verseValide).toBe(true);
  });
});
