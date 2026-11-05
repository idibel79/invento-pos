package ma.invento.pos.tresorerie.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import ma.invento.pos.parametre.application.ParametreService;
import ma.invento.pos.tresorerie.api.TresorerieDtos.CalendrierDto;
import ma.invento.pos.tresorerie.api.TresorerieDtos.JourneeDto;
import ma.invento.pos.tresorerie.api.TresorerieDtos.LigneJourneeDto;
import ma.invento.pos.tresorerie.api.TresorerieDtos.SessionJourneeDto;
import ma.invento.pos.tresorerie.domain.StatutVersement;
import ma.invento.pos.tresorerie.infrastructure.HistoriqueCompteJdbc;
import ma.invento.pos.tresorerie.infrastructure.HistoriqueCompteJdbc.AjustementJour;
import ma.invento.pos.tresorerie.infrastructure.HistoriqueCompteJdbc.SessionJour;
import ma.invento.pos.tresorerie.infrastructure.HistoriqueCompteJdbc.VentesJour;
import ma.invento.pos.tresorerie.infrastructure.HistoriqueCompteJdbc.VersementJour;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historique du compte de l'utilisateur connecté (page Mon compte) : jours actifs d'un mois et détail
 * d'une journée, découpés dans le fuseau de la boutique.
 */
@Service
public class HistoriqueCompteService {

    private final HistoriqueCompteJdbc historique;
    private final TresorerieService tresorerie;
    private final ParametreService parametres;
    private final Clock clock;

    public HistoriqueCompteService(
            HistoriqueCompteJdbc historique, TresorerieService tresorerie, ParametreService parametres, Clock clock) {
        this.historique = historique;
        this.tresorerie = tresorerie;
        this.parametres = parametres;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CalendrierDto calendrier(long utilisateurId, YearMonth mois) {
        ZoneId fuseau = fuseau();
        Instant debut = mois.atDay(1).atStartOfDay(fuseau).toInstant();
        Instant fin = mois.plusMonths(1).atDay(1).atStartOfDay(fuseau).toInstant();
        return new CalendrierDto(mois.toString(), historique.joursActifs(utilisateurId, debut, fin, fuseau.getId()));
    }

    @Transactional(readOnly = true)
    public JourneeDto journee(long utilisateurId, LocalDate jour) {
        ZoneId fuseau = fuseau();
        Instant debut = jour.atStartOfDay(fuseau).toInstant();
        Instant fin = jour.plusDays(1).atStartOfDay(fuseau).toInstant();
        boolean aujourdhui = jour.equals(LocalDate.now(clock.withZone(fuseau)));

        List<SessionJour> sessions = historique.sessions(utilisateurId, debut, fin);
        List<VentesJour> ventes = historique.ventes(utilisateurId, debut, fin);
        List<VersementJour> versements = historique.versements(utilisateurId, debut, fin);
        List<AjustementJour> ajustements = historique.ajustements(utilisateurId, debut, fin);

        List<LigneJourneeDto> lignes = new ArrayList<>();
        for (SessionJour s : sessions) {
            if (!s.ouverture().isBefore(debut)) lignes.add(LigneJourneeDto.ouverture(s));
            if (s.fermeture() != null && s.fermeture().isBefore(fin)) lignes.add(LigneJourneeDto.cloture(s));
        }
        ventes.forEach(v -> lignes.add(LigneJourneeDto.ventes(v)));
        versements.forEach(v -> lignes.add(LigneJourneeDto.versement(v)));
        ajustements.forEach(a -> lignes.add(LigneJourneeDto.ajustement(a)));
        lignes.sort(Comparator.comparing(LigneJourneeDto::le).reversed());

        BigDecimal soldeDebut = historique.soldeAvant(utilisateurId, debut).orElse(BigDecimal.ZERO);
        BigDecimal soldeFin = aujourdhui
                ? tresorerie.solde(utilisateurId)
                : historique.soldeAvant(utilisateurId, fin).orElse(soldeDebut);
        BigDecimal totalVentes =
                ventes.stream().map(v -> v.especes().add(v.carte())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal recu = versements.stream()
                .filter(v -> !v.versLaPrincipale() && v.statut() == StatutVersement.VALIDE)
                .map(VersementJour::montant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal verse = versements.stream()
                .filter(v -> v.versLaPrincipale()
                        && (v.statut() == StatutVersement.EN_TRANSIT || v.statut() == StatutVersement.VALIDE))
                .map(VersementJour::montant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new JourneeDto(
                jour,
                sessions.stream().map(SessionJourneeDto::depuis).toList(),
                soldeDebut,
                recu,
                totalVentes,
                verse,
                soldeFin,
                lignes);
    }

    private ZoneId fuseau() {
        return ZoneId.of(parametres.lire().fuseauHoraire());
    }
}
