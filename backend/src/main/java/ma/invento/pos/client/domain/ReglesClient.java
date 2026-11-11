package ma.invento.pos.client.domain;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import ma.invento.pos.shared.domain.RegleMetierException;

/** Règles de l'annuaire des clients et de leur dette, sans dépendance technique. */
public final class ReglesClient {

    public static final int NOM_MAX = 120;
    public static final int TELEPHONE_MAX = 30;
    public static final int NOTE_MAX = 300;
    public static final int MOTIF_MAX = 300;
    public static final int ICE_LONGUEUR = 15;
    public static final int IDENTIFIANT_FISCAL_MAX = 20;
    public static final int RC_MAX = 30;
    public static final int CONTACT_MAX = 120;
    public static final int ADRESSE_MAX = 300;
    public static final int EMAIL_MAX = 120;

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private ReglesClient() {}

    /**
     * Fiche nettoyée et validée. Le type est particulier s'il est absent ; pour un particulier, les
     * champs propres à l'entreprise sont ignorés.
     */
    public static FicheClient fiche(
            TypeClient type,
            String nom,
            String telephone,
            String note,
            BigDecimal plafond,
            String ice,
            String identifiantFiscal,
            String rc,
            String contact,
            String adresse,
            String email) {
        TypeClient leType = type == null ? TypeClient.PARTICULIER : type;
        boolean entreprise = leType == TypeClient.ENTREPRISE;
        return new FicheClient(
                leType,
                nom(nom, entreprise),
                facultatif(telephone, TELEPHONE_MAX, "Le téléphone"),
                facultatif(note, NOTE_MAX, "La note"),
                plafond(plafond),
                entreprise ? ice(ice) : null,
                entreprise ? facultatif(identifiantFiscal, IDENTIFIANT_FISCAL_MAX, "L'IF") : null,
                entreprise ? facultatif(rc, RC_MAX, "Le RC") : null,
                entreprise ? facultatif(contact, CONTACT_MAX, "Le contact") : null,
                entreprise ? facultatif(adresse, ADRESSE_MAX, "L'adresse") : null,
                entreprise ? email(email) : null);
    }

    /** Nom nettoyé, obligatoire. */
    public static String nom(String nom) {
        return nom(nom, false);
    }

    private static String nom(String nom, boolean entreprise) {
        String libelle = entreprise ? "La raison sociale" : "Le nom du client";
        String propre = nom == null ? "" : nom.strip().replaceAll("\\s+", " ");
        if (propre.isEmpty()) throw new RegleMetierException(libelle + " est obligatoire.");
        if (propre.length() > NOM_MAX) {
            throw new RegleMetierException(libelle + " ne doit pas dépasser " + NOM_MAX + " caractères.");
        }
        return propre;
    }

    /** ICE facultatif : 15 chiffres, espaces ignorés. */
    public static String ice(String ice) {
        String propre = ice == null ? "" : ice.replaceAll("\\s+", "");
        if (propre.isEmpty()) return null;
        if (propre.length() != ICE_LONGUEUR || !propre.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new RegleMetierException("L'ICE compte " + ICE_LONGUEUR + " chiffres.");
        }
        return propre;
    }

    /** Email facultatif, de forme nom@domaine.ext. */
    public static String email(String email) {
        String propre = facultatif(email, EMAIL_MAX, "L'email");
        if (propre != null && !EMAIL.matcher(propre).matches()) {
            throw new RegleMetierException("Adresse email invalide.");
        }
        return propre;
    }

    /** Texte facultatif nettoyé : null s'il est vide. */
    public static String facultatif(String texte, int max, String libelle) {
        String propre = texte == null ? "" : texte.strip();
        if (propre.isEmpty()) return null;
        if (propre.length() > max) {
            throw new RegleMetierException(libelle + " ne doit pas dépasser " + max + " caractères.");
        }
        return propre;
    }

    /**
     * Remboursement : parts en espèces et par TPE positives ou nulles, au centime, dont le total est
     * strictement positif. Il peut dépasser la dette : le surplus devient une avance du client.
     *
     * @return le total remboursé
     */
    public static BigDecimal remboursement(BigDecimal especes, BigDecimal tpe) {
        BigDecimal e = especes == null ? BigDecimal.ZERO : especes;
        BigDecimal t = tpe == null ? BigDecimal.ZERO : tpe;
        if (e.signum() < 0 || t.signum() < 0) {
            throw new RegleMetierException("Les montants ne peuvent pas être négatifs.");
        }
        if (e.stripTrailingZeros().scale() > 2 || t.stripTrailingZeros().scale() > 2) {
            throw new RegleMetierException("Le montant se saisit au centime près.");
        }
        BigDecimal total = e.add(t);
        if (total.signum() <= 0) throw new RegleMetierException("Indiquez le montant remboursé.");
        return total;
    }

    /** Plafond de crédit facultatif : absent, pas de limite ; sinon strictement positif, au centime. */
    public static BigDecimal plafond(BigDecimal plafond) {
        if (plafond == null) return null;
        if (plafond.signum() <= 0) throw new RegleMetierException("Le plafond doit être supérieur à 0.");
        if (plafond.stripTrailingZeros().scale() > 2) {
            throw new RegleMetierException("Le plafond se saisit au centime près.");
        }
        return plafond;
    }

    /** Une vente à crédit porte sur un montant strictement positif. */
    public static void verifierVenteACredit(BigDecimal total) {
        if (total == null || total.signum() <= 0) {
            throw new RegleMetierException("Une vente à 0 DH ne peut pas être payée à crédit.");
        }
    }
}
