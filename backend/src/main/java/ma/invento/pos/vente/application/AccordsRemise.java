package ma.invento.pos.vente.application;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ma.invento.pos.identite.application.AutorisationService;
import ma.invento.pos.identite.application.AutorisationService.Autorisation;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
import ma.invento.pos.shared.domain.ObjetAutorisation;
import ma.invento.pos.shared.domain.OperationInterditeException;
import ma.invento.pos.shared.domain.RegleMetierException;
import ma.invento.pos.vente.api.VenteDtos.AccordRemiseDto;
import ma.invento.pos.vente.api.VenteDtos.LigneCommande;
import ma.invento.pos.vente.api.VenteDtos.PanierCommande;
import ma.invento.pos.vente.domain.CalculateurVente;
import ma.invento.pos.vente.domain.LignePanier;
import ma.invento.pos.vente.domain.LigneVente;
import ma.invento.pos.vente.domain.NiveauAutorisation;
import ma.invento.pos.vente.domain.PanierCalcule;
import ma.invento.pos.vente.domain.PanierCalcule.LigneCalculee;
import ma.invento.pos.vente.domain.Remise;
import ma.invento.pos.vente.domain.Vente;
import org.springframework.stereotype.Component;

/**
 * Qui accorde chaque remise d'un panier. Le vendeur accorde lui-même celles que ses seuils couvrent
 * (responsable) ou toutes (admin) ; un caissier aucune. Sinon chaque remise, sur un article ou sur le
 * ticket, demande sa propre autorisation : un jeton signé pour cette seule remise par un responsable
 * dont les seuils la couvrent, ou par l'admin. Une vente reprise garde les autorisations accordées,
 * tant que la remise de l'article (ou du ticket) n'augmente pas. Les seuils sont relus en base.
 */
@Component
class AccordsRemise {

    private final UtilisateurRepository utilisateurs;
    private final AutorisationService autorisations;

    AccordsRemise(UtilisateurRepository utilisateurs, AutorisationService autorisations) {
        this.utilisateurs = utilisateurs;
        this.autorisations = autorisations;
    }

    Accords accorder(PanierCalcule calcul, PanierCommande commande, UtilisateurConnecte vendeur, Vente reprise) {
        if (!NiveauAutorisation.aUneRemise(calcul)) return new Accords(null, Map.of());
        Contexte contexte = new Contexte(
                vendeur,
                actif(vendeur.id()).orElse(null),
                utilisateurs.findAutorisateurs(List.of(Role.RESPONSABLE)).stream()
                        .filter(u -> !u.isVerrouille())
                        .toList());

        Accord ticket = null;
        Remise remiseTicket = Remise.ou(
                commande.remiseTicket() == null ? null : commande.remiseTicket().type(),
                commande.remiseTicket() == null ? null : commande.remiseTicket().valeur());
        if (remiseTicket != null && calcul.remiseTicket().signum() > 0) {
            BigDecimal taux = calcul.tauxRemiseMax();
            Long dejaAccorde =
                    reprise != null && reprise.getRemiseTicket() != null && taux.compareTo(tauxAccorde(reprise)) <= 0
                            ? reprise.getValideePar()
                            : null;
            ticket = accord(contexte, null, taux, true, remiseTicket, commande.jetonAutorisation(), dejaAccorde);
        }

        Map<Long, String> jetons = commande.lignes().stream()
                .filter(l ->
                        l.jetonAutorisation() != null && !l.jetonAutorisation().isBlank())
                .collect(Collectors.toMap(LigneCommande::produitId, LigneCommande::jetonAutorisation, (a, b) -> a));
        Map<Long, Accord> lignes = new LinkedHashMap<>();
        for (LigneCalculee ligne : calcul.lignes()) {
            if (ligne.remiseMontant().signum() == 0) continue;
            long produitId = ligne.ligne().produitId();
            BigDecimal taux = ligne.tauxRemise();
            Long dejaAccorde = reprise == null
                    ? null
                    : reprise.getLignes().stream()
                            .filter(l -> l.getProduitId().equals(produitId) && taux.compareTo(l.tauxRemise()) <= 0)
                            .map(LigneVente::getRemiseValideePar)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
            lignes.put(
                    produitId,
                    accord(
                            contexte,
                            produitId,
                            taux,
                            false,
                            ligne.ligne().remise(),
                            jetons.get(produitId),
                            dejaAccorde));
        }
        return new Accords(ticket, lignes);
    }

    private Accord accord(
            Contexte contexte,
            Long produitId,
            BigDecimal taux,
            boolean surTicket,
            Remise remise,
            String jeton,
            Long dejaAccorde) {
        if (couvre(contexte.vendeur(), taux, surTicket)) {
            return new Accord(
                    produitId,
                    taux,
                    NiveauAutorisation.AUCUNE,
                    contexte.vendeur().getId(),
                    null);
        }
        boolean unResponsable = contexte.responsables().stream().anyMatch(u -> couvre(u, taux, surTicket));
        NiveauAutorisation niveau = NiveauAutorisation.requis(false, unResponsable);
        try {
            if (jeton != null && !jeton.isBlank()) {
                Autorisation autorisation =
                        autorisations.verifier(jeton, contexte.connecte().id());
                String objet = ObjetAutorisation.remise(produitId, remise.type().name(), remise.valeur());
                if (!autorisation.objet().equals(objet)) {
                    throw new RegleMetierException(
                            "Autorisation donnée pour une autre remise : faites ressaisir le PIN.");
                }
                if (!couvre(actif(autorisation.autorisateurId()).orElse(null), taux, surTicket)) {
                    throw new OperationInterditeException(
                            "Cette remise dépasse les seuils de ce responsable : autorisation de l'admin requise.");
                }
                return new Accord(produitId, taux, niveau, autorisation.autorisateurId(), null);
            }
            if (dejaAccorde != null && couvre(actif(dejaAccorde).orElse(null), taux, surTicket)) {
                return new Accord(produitId, taux, niveau, dejaAccorde, null);
            }
            throw new OperationInterditeException(refus(contexte.connecte(), surTicket));
        } catch (RegleMetierException | OperationInterditeException e) {
            return new Accord(produitId, taux, niveau, null, e);
        }
    }

    private static String refus(UtilisateurConnecte vendeur, boolean surTicket) {
        if (vendeur.role() == Role.CAISSIER) {
            return surTicket
                    ? "Un caissier n'accorde pas de remise : la remise sur le ticket demande le PIN d'un responsable."
                    : "Un caissier n'accorde pas de remise : chaque article remisé demande le PIN d'un responsable.";
        }
        return "Cette remise dépasse vos seuils : autorisation de l'admin requise.";
    }

    private static boolean couvre(Utilisateur utilisateur, BigDecimal taux, boolean surTicket) {
        return utilisateur != null
                && NiveauAutorisation.couvre(utilisateur.getRole(), utilisateur.getSeuilsRemise(), taux, surTicket);
    }

    private Optional<Utilisateur> actif(long id) {
        return utilisateurs.findById(id).filter(Utilisateur::isActif);
    }

    /** Remise ticket effective de la vente en attente, telle qu'elle a été autorisée. */
    private static BigDecimal tauxAccorde(Vente vente) {
        List<LignePanier> lignes = vente.getLignes().stream()
                .map(l -> new LignePanier(
                        l.getProduitId(), l.getQuantite(), l.getPrixUnitaire(), l.getTauxTva(), l.getRemise()))
                .toList();
        return CalculateurVente.calculer(lignes, vente.getRemiseTicket()).tauxRemiseMax();
    }

    private record Contexte(UtilisateurConnecte connecte, Utilisateur vendeur, List<Utilisateur> responsables) {}

    /**
     * Autorisation d'une remise.
     *
     * @param produitId article, ou null pour le ticket
     * @param valideePar qui l'accorde, null si elle ne l'est pas encore
     * @param refus raison du refus, quand elle n'est pas accordée
     */
    record Accord(Long produitId, BigDecimal taux, NiveauAutorisation niveau, Long valideePar, RuntimeException refus) {

        AccordRemiseDto versDto() {
            return new AccordRemiseDto(produitId, taux, niveau, valideePar);
        }
    }

    /** Autorisations de la remise ticket (ou null) et de chaque article remisé, par produit. */
    record Accords(Accord ticket, Map<Long, Accord> lignes) {

        Stream<Accord> tous() {
            return Stream.concat(Stream.ofNullable(ticket), lignes.values().stream());
        }

        /** Niveau le plus exigeant : il décide si un motif est demandé. */
        NiveauAutorisation niveau() {
            return tous().map(Accord::niveau).max(Comparator.naturalOrder()).orElse(NiveauAutorisation.AUCUNE);
        }

        boolean acquis() {
            return tous().allMatch(a -> a.valideePar() != null);
        }

        /** Refuse la vente tant qu'une remise n'est pas accordée. */
        void exiger() {
            tous().filter(a -> a.refus() != null).findFirst().ifPresent(a -> {
                throw a.refus();
            });
        }

        /** Gardé sur la vente : qui a accordé la remise ticket, sinon celle d'un article. */
        Long principal() {
            return tous().map(Accord::valideePar)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        Map<Long, Long> parArticle() {
            return lignes.values().stream()
                    .filter(a -> a.valideePar() != null)
                    .collect(Collectors.toMap(Accord::produitId, Accord::valideePar));
        }

        List<AccordRemiseDto> versDto() {
            return tous().map(Accord::versDto).toList();
        }
    }
}
