package ma.invento.pos.parametre.application;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.parametre.domain.CleParametre;
import ma.invento.pos.parametre.domain.Parametre;
import ma.invento.pos.parametre.infrastructure.ParametreRepository;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Paramètres de la boutique, modifiables par l'admin ; chaque changement est journalisé. */
@Service
public class ParametreService {

    private final ParametreRepository repository;
    private final JournalService journal;

    public ParametreService(ParametreRepository repository, JournalService journal) {
        this.repository = repository;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    public Parametres lire() {
        Map<String, String> valeurs =
                repository.findAll().stream().collect(Collectors.toMap(Parametre::getCle, Parametre::getValeur));
        return new Parametres(
                valeurs.get(CleParametre.BOUTIQUE_NOM.cle()),
                valeurs.get(CleParametre.BOUTIQUE_ADRESSE.cle()),
                valeurs.getOrDefault(CleParametre.BOUTIQUE_TELEPHONE.cle(), ""),
                valeurs.getOrDefault(CleParametre.BOUTIQUE_EMAIL.cle(), ""),
                valeurs.getOrDefault(CleParametre.BOUTIQUE_ICE.cle(), ""),
                valeurs.getOrDefault(CleParametre.BOUTIQUE_IF.cle(), ""),
                valeurs.getOrDefault(CleParametre.BOUTIQUE_RC.cle(), ""),
                valeurs.getOrDefault(CleParametre.BOUTIQUE_PATENTE.cle(), ""),
                valeurs.getOrDefault(CleParametre.TICKET_MESSAGE.cle(), ""),
                valeurs.get(CleParametre.FUSEAU_HORAIRE.cle()));
    }

    @Transactional
    public Parametres modifier(Parametres nouveaux, long adminId) {
        verifierFuseau(nouveaux.fuseauHoraire());

        Map<CleParametre, String> voulus = new EnumMap<>(CleParametre.class);
        voulus.put(CleParametre.BOUTIQUE_NOM, nouveaux.boutiqueNom().trim());
        voulus.put(CleParametre.BOUTIQUE_ADRESSE, nouveaux.boutiqueAdresse().trim());
        voulus.put(CleParametre.BOUTIQUE_TELEPHONE, sansNull(nouveaux.boutiqueTelephone()));
        voulus.put(CleParametre.BOUTIQUE_EMAIL, sansNull(nouveaux.boutiqueEmail()));
        voulus.put(CleParametre.BOUTIQUE_ICE, sansNull(nouveaux.boutiqueIce()));
        voulus.put(CleParametre.BOUTIQUE_IF, sansNull(nouveaux.boutiqueIf()));
        voulus.put(CleParametre.BOUTIQUE_RC, sansNull(nouveaux.boutiqueRc()));
        voulus.put(CleParametre.BOUTIQUE_PATENTE, sansNull(nouveaux.boutiquePatente()));
        voulus.put(CleParametre.TICKET_MESSAGE, sansNull(nouveaux.ticketMessage()));
        voulus.put(CleParametre.FUSEAU_HORAIRE, nouveaux.fuseauHoraire());

        Map<String, Parametre> existants =
                repository.findAll().stream().collect(Collectors.toMap(Parametre::getCle, Function.identity()));
        voulus.forEach((cle, valeur) -> {
            Parametre parametre = existants.get(cle.cle());
            if (parametre == null) {
                repository.save(new Parametre(cle.cle(), valeur));
                journal.enregistrer(adminId, ActionJournal.PARAMETRE_MODIFIE, cle.cle() + " = " + valeur);
            } else if (!Objects.equals(parametre.getValeur(), valeur)) {
                journal.enregistrer(
                        adminId,
                        ActionJournal.PARAMETRE_MODIFIE,
                        cle.cle() + " : " + parametre.getValeur() + " -> " + valeur);
                parametre.setValeur(valeur);
            }
        });
        return lire();
    }

    /** Les champs facultatifs du ticket absents de la requête sont enregistrés vides. */
    private static String sansNull(String valeur) {
        return valeur == null ? "" : valeur.trim();
    }

    private static void verifierFuseau(String fuseau) {
        try {
            ZoneId.of(fuseau);
        } catch (DateTimeException | NullPointerException e) {
            throw new RegleMetierException("Fuseau horaire inconnu : " + fuseau);
        }
    }

    /**
     * En-tête et pied du ticket de caisse, identifiants légaux imprimés sur la facture A4 (ICE, IF, RC,
     * patente, facultatifs) et fuseau horaire de la boutique.
     */
    public record Parametres(
            String boutiqueNom,
            String boutiqueAdresse,
            String boutiqueTelephone,
            String boutiqueEmail,
            String boutiqueIce,
            String boutiqueIf,
            String boutiqueRc,
            String boutiquePatente,
            String ticketMessage,
            String fuseauHoraire) {}
}
