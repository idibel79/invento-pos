package ma.invento.pos.importation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import ma.invento.pos.shared.domain.RegleMetierException;

/**
 * Lit les lignes d'un export Sobrus ou Invento (première ligne = en-têtes) : détection du
 * format, choix de l'inventaire, lecture de chaque ligne, PID et codes-barres en double.
 */
public final class AnalyseurFichier {

    private static final List<Colonne> COLONNES_OBLIGATOIRES =
            List.of(Colonne.PID, Colonne.PRODUIT, Colonne.CATEGORIE, Colonne.TVA, Colonne.PPV, Colonne.STOCK);
    private static final Pattern EPOCH_MILLIS = Pattern.compile("\\d{10,}");

    private AnalyseurFichier() {}

    /**
     * @param inventaireChoisi numéro d'inventaire à importer (export Invento) ; par défaut le
     *     plus récent
     */
    public static FichierCatalogue analyser(List<List<String>> lignes, String inventaireChoisi) {
        if (lignes.isEmpty()) throw new RegleMetierException("Le fichier ne contient aucune ligne.");
        Map<Colonne, Integer> index = indexerEntetes(lignes.getFirst());
        FormatImport format = index.containsKey(Colonne.QUANTITE_REELLE) || index.containsKey(Colonne.NUMERO_INVENTAIRE)
                ? FormatImport.INVENTO
                : FormatImport.SOBRUS;

        List<LigneNumerotee> donnees = new ArrayList<>();
        for (int i = 1; i < lignes.size(); i++) {
            Map<Colonne, String> valeurs = valeurs(lignes.get(i), index);
            if (valeurs.values().stream().anyMatch(v -> !v.isBlank())) {
                donnees.add(new LigneNumerotee(i + 1, valeurs)); // numéro tel qu'affiché par Excel
            }
        }

        List<InventaireDisponible> inventaires = List.of();
        InventaireDisponible retenu = null;
        if (format == FormatImport.INVENTO) {
            inventaires = inventaires(donnees);
            retenu = choisir(inventaires, inventaireChoisi);
            String numero = retenu.numero();
            donnees = donnees.stream()
                    .filter(l -> numero.equals(numeroInventaire(l)))
                    .toList();
        }

        List<String> erreurs = new ArrayList<>();
        List<String> avertissements = new ArrayList<>();
        Map<String, LigneCatalogue> parPid = new LinkedHashMap<>();
        Set<String> codesVus = new HashSet<>();
        for (LigneNumerotee ligne : donnees) {
            try {
                LigneCatalogue lue = LecteurLigne.lire(ligne.numero(), ligne.valeurs(), format, avertissements::add);
                if (parPid.containsKey(lue.pid())) {
                    erreurs.add("Ligne " + ligne.numero() + " ignorée : PID " + lue.pid()
                            + " déjà présent (première occurrence conservée).");
                    continue;
                }
                parPid.put(lue.pid(), sansCodesEnDouble(lue, codesVus, avertissements));
            } catch (LigneInvalideException e) {
                erreurs.add("Ligne " + ligne.numero() + " ignorée : " + e.getMessage());
            }
        }
        return new FichierCatalogue(
                format,
                inventaires,
                retenu,
                List.copyOf(parPid.values()),
                List.copyOf(erreurs),
                List.copyOf(avertissements));
    }

    private static Map<Colonne, Integer> indexerEntetes(List<String> entetes) {
        Map<String, Colonne> parCle = new HashMap<>();
        for (Colonne colonne : Colonne.values()) parCle.put(colonne.cle(), colonne);
        Map<Colonne, Integer> index = new EnumMap<>(Colonne.class);
        for (int i = 0; i < entetes.size(); i++) {
            Colonne colonne = parCle.get(Colonne.normaliser(entetes.get(i)));
            if (colonne != null) index.putIfAbsent(colonne, i);
        }
        List<String> manquantes = COLONNES_OBLIGATOIRES.stream()
                .filter(c -> !index.containsKey(c))
                .map(Colonne::cle)
                .toList();
        if (!manquantes.isEmpty()) {
            throw new RegleMetierException(
                    "Format non reconnu (export Sobrus ou Invento attendu). Colonnes manquantes : " + manquantes);
        }
        return index;
    }

    private static Map<Colonne, String> valeurs(List<String> cellules, Map<Colonne, Integer> index) {
        Map<Colonne, String> valeurs = new EnumMap<>(Colonne.class);
        index.forEach((colonne, i) ->
                valeurs.put(colonne, i < cellules.size() && cellules.get(i) != null ? cellules.get(i) : ""));
        return valeurs;
    }

    /** Un même code-barre ne peut désigner qu'un produit : seule sa première occurrence est gardée. */
    private static LigneCatalogue sansCodesEnDouble(
            LigneCatalogue ligne, Set<String> codesVus, List<String> avertissements) {
        List<String> codes = new ArrayList<>();
        for (String code : ligne.codesBarres()) {
            if (codesVus.add(code)) {
                codes.add(code);
            } else {
                avertissements.add("Ligne " + ligne.numeroLigne() + " : code-barre " + code
                        + " déjà utilisé par une ligne précédente, ignoré.");
            }
        }
        if (codes.size() == ligne.codesBarres().size()) return ligne;
        return new LigneCatalogue(
                ligne.numeroLigne(),
                ligne.pid(),
                ligne.designation(),
                ligne.categorie(),
                ligne.tauxMarge(),
                ligne.tvaLibelle(),
                ligne.tvaTaux(),
                ligne.zone(),
                ligne.ppv(),
                ligne.pph(),
                ligne.stock(),
                ligne.stockMin(),
                ligne.stockMax(),
                ligne.datePeremption(),
                List.copyOf(codes));
    }

    /** Inventaires de l'export, du plus récent au plus ancien. */
    private static List<InventaireDisponible> inventaires(List<LigneNumerotee> lignes) {
        Map<String, Instant> dates = new LinkedHashMap<>();
        Map<String, Integer> nombres = new HashMap<>();
        for (LigneNumerotee ligne : lignes) {
            String numero = numeroInventaire(ligne);
            dates.putIfAbsent(numero, date(ligne.valeurs().getOrDefault(Colonne.DATE_INVENTAIRE, "")));
            nombres.merge(numero, 1, Integer::sum);
        }
        return dates.entrySet().stream()
                .map(e -> new InventaireDisponible(e.getKey(), e.getValue(), nombres.get(e.getKey())))
                .sorted(Comparator.comparing(
                                InventaireDisponible::date, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(InventaireDisponible::numero, Comparator.reverseOrder()))
                .toList();
    }

    private static InventaireDisponible choisir(List<InventaireDisponible> inventaires, String choisi) {
        if (inventaires.isEmpty()) throw new RegleMetierException("Le fichier ne contient aucun article.");
        if (choisi == null || choisi.isBlank()) return inventaires.getFirst();
        return inventaires.stream()
                .filter(i -> i.numero().equals(choisi.trim()))
                .findFirst()
                .orElseThrow(() -> new RegleMetierException("Inventaire " + choisi + " absent du fichier."));
    }

    private static String numeroInventaire(LigneNumerotee ligne) {
        return ligne.valeurs().getOrDefault(Colonne.NUMERO_INVENTAIRE, "").trim();
    }

    /** Invento exporte la date de création en millisecondes depuis 1970 ; ISO accepté aussi. */
    private static Instant date(String texte) {
        String valeur = texte.trim();
        if (valeur.isEmpty()) return null;
        try {
            if (EPOCH_MILLIS.matcher(valeur).matches()) return Instant.ofEpochMilli(Long.parseLong(valeur));
            if (valeur.length() == 10)
                return LocalDate.parse(valeur).atStartOfDay().toInstant(ZoneOffset.UTC);
            return Instant.parse(valeur);
        } catch (DateTimeParseException | NumberFormatException e) {
            return null;
        }
    }

    private record LigneNumerotee(int numero, Map<Colonne, String> valeurs) {}
}
