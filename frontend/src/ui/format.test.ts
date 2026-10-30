import { describe, expect, it } from "vitest";
import { dh, lireMontant, pourcent } from "./format";

const espaces = (texte: string) => texte.replace(/[\u202f\u00a0]/g, " ");

describe("format", () => {
  it("affiche les montants en dirhams avec deux décimales", () => {
    expect(espaces(dh(260.6))).toBe("260,60 DH");
    expect(espaces(dh(1234.5))).toBe("1 234,50 DH");
    expect(espaces(pourcent(12.5))).toBe("12,5 %");
  });

  it("lit les saisies de montant", () => {
    expect(lireMontant("300")).toBe(300);
    expect(lireMontant("300,5")).toBe(300.5);
    expect(lireMontant(" 1 234.56 ")).toBe(1234.56);
    expect(lireMontant("12,345")).toBeNull();
    expect(lireMontant("-5")).toBeNull();
    expect(lireMontant("abc")).toBeNull();
    expect(lireMontant("")).toBeNull();
  });
});
