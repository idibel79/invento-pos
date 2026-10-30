package ma.invento.pos.importation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Règles de lecture d'une ligne (cadrage § 2 et 3) : catégorie séparée de son taux de marge,
 * TVA convertie en taux, prix texte convertis en décimal, zone vide = « Non assigné », stock vide
 * = 0, et pour Invento stock = quantité réelle quand l'article a été compté.
 */
public final class LecteurLigne {

    public static final String ZONE_NON_ASSIGNEE = "Non assigné";
    public static final String CATEGORIE_NON_CLASSEE = "Non classé";

    /** {@code "Médicament (33.930%)"} ou {@code "TVA (20.00%)"}. */
    private static final Pattern NOM_ET_TAUX = Pattern.compile("^(.*?)\\s*\\(\\s*(\\d+(?:[.,]\\d+)?)\\s*%\\s*\\)\\s*$");

    private static final Pattern TAUX_SEUL = Pattern.compile("^(\\d+(?:[.,]\\d+)?)\\s*%?$");
    private static final List<DateTimeFormatter> FORMATS_DATE = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"));

    private LecteurLigne() {}

    /**
     * @param valeurs cellules de la ligne indexées par colonne (texte brut, déjà réparé)
     * @param avertissements reçoit les anomalies non bloquantes (ex. date illisible)
     * @throws LigneInvalideException si une donnée obligatoire manque ou est illisible
     */
    public static LigneCatalogue lire(
            int numeroLigne, Map<Colonne, String> valeurs, FormatImport format, Consumer<String> avertissements) {
        String pid = obligatoire(valeurs, Colonne.PID, "PID");
        String designation = obligatoire(valeurs, Colonne.PRODUIT, "désignation");

        String categorieBrute = texte(valeurs, Colonne.CATEGORIE);
        Matcher categorie = NOM_ET_TAUX.matcher(categorieBrute);
        String nomCategorie = categorie.matches() ? nettoyer(categorie.group(1)) : nettoyer(categorieBrute);
        BigDecimal tauxMarge = categorie.matches() ? taux(categorie.group(2), 3) : null;

        Tva tva = lireTva(texte(valeurs, Colonne.TVA));

        String zone = nettoyer(texte(valeurs, Colonne.ZONE));

        BigDecimal ppv = prix(obligatoire(valeurs, Colonne.PPV, "PPV"), "PPV");
        String pphTexte = texte(valeurs, Colonne.PPH);
        BigDecimal pph = pphTexte.isEmpty() ? null : prix(pphTexte, "PPH");

        int stock = stock(valeurs, format);

        List<String> codes = new ArrayList<>(2);
        for (Colonne colonne : List.of(Colonne.CODE_BARRE_1, Colonne.CODE_BARRE_2)) {
            String code = texte(valeurs, colonne);
            if (!code.isEmpty() && !codes.contains(code)) codes.add(code);
        }

        return new LigneCatalogue(
                numeroLigne,
                pid,
                designation,
                nomCategorie.isEmpty() ? CATEGORIE_NON_CLASSEE : nomCategorie,
                tauxMarge,
                tva.libelle(),
                tva.taux(),
                zone.isEmpty() ? ZONE_NON_ASSIGNEE : zone,
                ppv,
                pph,
                stock,
                entierFacultatif(texte(valeurs, Colonne.STOCK_MIN), "stock min"),
                entierFacultatif(texte(valeurs, Colonne.STOCK_MAX), "stock max"),
                date(texte(valeurs, Colonne.DATE_PEREMPTION), numeroLigne, avertissements),
                List.copyOf(codes));
    }

    /** Invento : quantité réelle si l'article a été compté, sinon stock théorique. */
    private static int stock(Map<Colonne, String> valeurs, FormatImport format) {
        String quantiteReelle = format == FormatImport.INVENTO ? texte(valeurs, Colonne.QUANTITE_REELLE) : "";
        String source = quantiteReelle.isEmpty() ? texte(valeurs, Colonne.STOCK) : quantiteReelle;
        return source.isEmpty() ? 0 : entier(source, "stock");
    }

    private static Tva lireTva(String brute) {
        if (brute.isEmpty()) throw new LigneInvalideException("TVA manquante.");
        Matcher nomEtTaux = NOM_ET_TAUX.matcher(brute);
        if (nomEtTaux.matches()) {
            BigDecimal taux = taux(nomEtTaux.group(2), 2);
            String libelle = nettoyer(nomEtTaux.group(1));
            return new Tva(libelle.isEmpty() ? "TVA " + taux.toPlainString() + " %" : libelle, verifierTaux(taux));
        }
        Matcher seul = TAUX_SEUL.matcher(brute.trim());
        if (seul.matches()) {
            BigDecimal taux = taux(seul.group(1), 2);
            return new Tva("TVA " + taux.toPlainString() + " %", verifierTaux(taux));
        }
        throw new LigneInvalideException("TVA illisible : " + brute);
    }

    private static BigDecimal verifierTaux(BigDecimal taux) {
        if (taux.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new LigneInvalideException("Taux de TVA supérieur à 100 %.");
        }
        return taux;
    }

    private static BigDecimal prix(String texte, String nom) {
        BigDecimal valeur = decimal(texte, nom).setScale(2, RoundingMode.HALF_UP);
        if (valeur.signum() < 0) throw new LigneInvalideException(nom + " négatif : " + texte);
        return valeur;
    }

    private static BigDecimal taux(String texte, int echelle) {
        return new BigDecimal(texte.replace(',', '.')).setScale(echelle, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(String texte, String nom) {
        try {
            return new BigDecimal(texte.replace(' ', ' ').replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new LigneInvalideException(nom + " illisible : " + texte);
        }
    }

    private static int entier(String texte, String nom) {
        try {
            return decimal(texte, nom).stripTrailingZeros().intValueExact();
        } catch (ArithmeticException e) {
            throw new LigneInvalideException(nom + " non entier : " + texte);
        }
    }

    private static Integer entierFacultatif(String texte, String nom) {
        return texte.isEmpty() ? null : entier(texte, nom);
    }

    /** La date de péremption est informative : illisible, elle est ignorée avec un avertissement. */
    private static LocalDate date(String texte, int numeroLigne, Consumer<String> avertissements) {
        if (texte.isEmpty()) return null;
        for (DateTimeFormatter format : FORMATS_DATE) {
            try {
                return LocalDate.parse(texte, format);
            } catch (DateTimeParseException ignored) {
                // format suivant
            }
        }
        avertissements.accept("Ligne " + numeroLigne + " : date de péremption illisible (" + texte + "), ignorée.");
        return null;
    }

    private static String obligatoire(Map<Colonne, String> valeurs, Colonne colonne, String nom) {
        String valeur = texte(valeurs, colonne);
        if (valeur.isEmpty()) throw new LigneInvalideException(nom + " manquant(e).");
        return valeur;
    }

    private static String texte(Map<Colonne, String> valeurs, Colonne colonne) {
        String valeur = valeurs.get(colonne);
        return valeur == null ? "" : valeur.trim();
    }

    /** Espaces de fin supprimés et espaces multiples réduits (zones Sobrus « Comptoir  »). */
    static String nettoyer(String texte) {
        return texte.replace(' ', ' ').trim().replaceAll("\\s+", " ");
    }

    private record Tva(String libelle, BigDecimal taux) {}
}
