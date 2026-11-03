import { describe, expect, it } from "vitest";
import { saisirTouche } from "./PaveNumerique";

describe("saisirTouche", () => {
  it("compose un montant décimal", () => {
    expect(saisirTouche("", "5")).toBe("5");
    expect(saisirTouche("0", "5")).toBe("5");
    expect(saisirTouche("", ",")).toBe("0,");
    expect(saisirTouche("12", ",")).toBe("12,");
    expect(saisirTouche("12,5", "0")).toBe("12,50");
  });

  it("ignore une touche qui rendrait le montant invalide", () => {
    expect(saisirTouche("12,", ",")).toBe("12,");
    expect(saisirTouche("12,50", "1")).toBe("12,50");
  });

  it("efface le dernier caractère", () => {
    expect(saisirTouche("12,5", "retour")).toBe("12,");
    expect(saisirTouche("", "retour")).toBe("");
  });
});
