import { describe, expect, it } from "vitest";
import { calculerEchelle } from "./echelle";

describe("calculerEchelle", () => {
  it("retrouve le zoom à 90 % sur l'écran de référence", () => {
    expect(calculerEchelle(1536)).toBe(0.9);
  });

  it("suit la largeur des écrans de poste de caisse", () => {
    expect(calculerEchelle(1024)).toBe(0.6);
    expect(calculerEchelle(1920)).toBe(1.125);
    expect(calculerEchelle(3840)).toBe(2.25);
  });

  it("plafonne sur un très grand écran", () => {
    expect(calculerEchelle(8000)).toBe(2.5);
  });

  it("revient à l'échelle 1 sous 1024 px (tablette, fort zoom de Chrome)", () => {
    expect(calculerEchelle(1023)).toBe(1);
    expect(calculerEchelle(768)).toBe(1);
  });
});
