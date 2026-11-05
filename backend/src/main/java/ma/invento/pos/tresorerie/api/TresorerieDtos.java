package ma.invento.pos.tresorerie.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.tresorerie.domain.StatutVersement;
import ma.invento.pos.tresorerie.infrastructure.HistoriqueCompteJdbc.AjustementJour;
import ma.invento.pos.tresorerie.infrastructure.HistoriqueCompteJdbc.SessionJour;
import ma.invento.pos.tresorerie.infrastructure.HistoriqueCompteJdbc.VentesJour;
import ma.invento.pos.tresorerie.infrastructure.HistoriqueCompteJdbc.VersementJour;
import ma.invento.pos.tresorerie.infrastructure.VersementsJdbc.Versement;

/** Contrats de l'API de trésorerie (caisse principale et comptes des caissiers). */
public final class TresorerieDtos {

    private TresorerieDtos() {}

    /**
     * Versement entre un caissier ({@code caissier}) et la caisse principale ; {@code versLaPrincipale}
     * donne le sens.
     */
    public record VersementDto(
            Long id,
            BigDecimal montant,
            StatutVersement statut,
            boolean versLaPrincipale,
            Long caissierId,
            String caissier,
            @Schema(nullable = true) String motif,
            boolean cloture,
            boolean autoValide,
            Instant creeLe,
            @Schema(nullable = true) Instant traiteLe) {

        public static VersementDto depuis(Versement v) {
            return new VersementDto(
                    v.id(),
                    v.montant(),
                    v.statut(),
                    v.versLaPrincipale(),
                    v.caissierId(),
                    v.caissier(),
                    v.motif(),
                    v.cloture(),
                    v.autoValide(),
                    v.creeLe(),
                    v.traiteLe());
        }
    }

    /**
     * Compte de l'utilisateur connecté : solde dû à la caisse principale (ventes de la session ouverte
     * comprises), attendu dans le tiroir si une session est ouverte, versements à valider et historique
     * du jour (avec les versements encore en transit).
     */
    public record MonCompteDto(
            BigDecimal solde,
            boolean sessionOuverte,
            @Schema(nullable = true) BigDecimal tiroirAttendu,
            List<VersementDto> aValider,
            List<VersementDto> historique) {}

    /** Compte d'un utilisateur vu par l'admin, avec ses versements en transit dans les deux sens. */
    public record CompteCaissierDto(
            Long utilisateurId,
            String nom,
            Role role,
            boolean actif,
            BigDecimal solde,
            @Schema(nullable = true) String sessionOuverteCaisse,
            int recusEnTransitNombre,
            BigDecimal recusEnTransitMontant,
            int envoyesEnTransitNombre,
            BigDecimal envoyesEnTransitMontant) {}

    /** Caisse principale et comptes des caissiers (écran Caisse principale de l'administration). */
    public record ComptesDto(
            BigDecimal soldePrincipale,
            int aValiderNombre,
            BigDecimal aValiderMontant,
            List<CompteCaissierDto> caissiers) {}

    /** Versement d'un caissier vers la caisse principale. */
    public record VersementCommande(
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
            BigDecimal montant,

            @Schema(nullable = true) UUID cle) {}

    /** Versement de la caisse principale à un caissier (admin). */
    public record VersementCaissierCommande(
            @NotNull Long utilisateurId,

            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
            BigDecimal montant,

            @Schema(nullable = true) @Size(max = 300) String motif,
            @Schema(nullable = true) UUID cle) {}

    /** Alimentation, retrait ou ajustement : le motif est obligatoire pour un retrait et un ajustement. */
    public record OperationCommande(
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
            BigDecimal montant,

            @Schema(nullable = true) @Size(max = 300) String motif,
            @Schema(nullable = true) UUID cle) {}

    public record RefusCommande(
            @NotNull @Size(min = 1, max = 300) String motif) {}

    // ---------------------------------------------------------------- Historique (page Mon compte)

    /** Jours du mois (AAAA-MM) où l'utilisateur a au moins une opération. */
    public record CalendrierDto(String mois, List<LocalDate> jours) {}

    public record SessionJourneeDto(
            String caisse,
            Instant ouverture,
            @Schema(nullable = true) Instant fermeture) {
        public static SessionJourneeDto depuis(SessionJour s) {
            return new SessionJourneeDto(s.caisse(), s.ouverture(), s.fermeture());
        }
    }

    /**
     * Journée du compte : soldes de début et de fin, reçu (versements validés de la caisse principale),
     * ventes, versé (versements vers la caisse principale ni refusés ni annulés) et chronologie.
     */
    public record JourneeDto(
            LocalDate jour,
            List<SessionJourneeDto> sessions,
            BigDecimal soldeDebut,
            BigDecimal recu,
            BigDecimal ventes,
            BigDecimal verse,
            BigDecimal soldeFin,
            List<LigneJourneeDto> lignes) {}

    public enum TypeLigneJournee {
        OUVERTURE,
        VENTES,
        CLOTURE,
        VERSEMENT,
        AJUSTEMENT
    }

    /**
     * Événement de la journée. {@code montant} : effet sur le solde dû (signé), sauf l'ouverture où
     * c'est le fond (pris sur le compte, sans effet sur le solde). Les autres champs ne valent que pour
     * leur type.
     */
    public record LigneJourneeDto(
            Instant le,
            TypeLigneJournee type,
            BigDecimal montant,
            @Schema(nullable = true) String caisse,
            @Schema(nullable = true) Integer tickets,
            @Schema(nullable = true) BigDecimal especes,
            @Schema(nullable = true) BigDecimal carte,
            @Schema(nullable = true) BigDecimal montantCompte,
            @Schema(nullable = true) BigDecimal ecart,
            boolean clotureForcee,
            boolean comptageEnAttente,
            @Schema(nullable = true) Boolean versLaPrincipale,
            @Schema(nullable = true) StatutVersement statut,
            boolean autoValide,
            boolean cloture,
            @Schema(nullable = true) String creePar,
            @Schema(nullable = true) String traitePar,
            @Schema(nullable = true) Instant traiteLe,
            @Schema(nullable = true) String motif) {

        private static LigneJourneeDto de(Instant le, TypeLigneJournee type, BigDecimal montant, String caisse) {
            return new LigneJourneeDto(
                    le, type, montant, caisse, null, null, null, null, null, false, false, null, null, false, false,
                    null, null, null, null);
        }

        public static LigneJourneeDto ouverture(SessionJour s) {
            return de(s.ouverture(), TypeLigneJournee.OUVERTURE, s.fond(), s.caisse());
        }

        /** Montant : l'excédent ajouté au solde (un manque ne le réduit pas). */
        public static LigneJourneeDto cloture(SessionJour s) {
            BigDecimal excedent =
                    s.ecart() == null ? BigDecimal.ZERO : s.ecart().max(BigDecimal.ZERO);
            return new LigneJourneeDto(
                    s.fermeture(),
                    TypeLigneJournee.CLOTURE,
                    excedent,
                    s.caisse(),
                    null,
                    null,
                    null,
                    s.montantCompte(),
                    s.ecart(),
                    s.clotureForcee(),
                    s.comptageEnAttente(),
                    null,
                    null,
                    false,
                    false,
                    null,
                    null,
                    null,
                    null);
        }

        public static LigneJourneeDto ventes(VentesJour v) {
            return new LigneJourneeDto(
                    v.derniere(),
                    TypeLigneJournee.VENTES,
                    v.especes().add(v.carte()),
                    v.caisse(),
                    v.tickets(),
                    v.especes(),
                    v.carte(),
                    null,
                    null,
                    false,
                    false,
                    null,
                    null,
                    false,
                    false,
                    null,
                    null,
                    null,
                    null);
        }

        public static LigneJourneeDto versement(VersementJour v) {
            return new LigneJourneeDto(
                    v.creeLe(),
                    TypeLigneJournee.VERSEMENT,
                    v.versLaPrincipale() ? v.montant().negate() : v.montant(),
                    v.caisse(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    v.versLaPrincipale(),
                    v.statut(),
                    v.autoValide(),
                    v.cloture(),
                    v.creePar(),
                    v.traitePar(),
                    v.traiteLe(),
                    v.motif());
        }

        public static LigneJourneeDto ajustement(AjustementJour a) {
            return new LigneJourneeDto(
                    a.le(),
                    TypeLigneJournee.AJUSTEMENT,
                    a.montant(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    null,
                    null,
                    false,
                    false,
                    a.auteur(),
                    null,
                    null,
                    a.motif());
        }
    }
}
