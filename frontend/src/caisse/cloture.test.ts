import { describe, expect, it } from "vitest";
import { apercuCloture } from "./cloture";

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
