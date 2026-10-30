package ma.invento.pos.catalogue.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Données modifiables d'une fiche produit (hors stock et codes-barres). */
public record FicheProduit(
        String designation,
        Categorie categorie,
        Tva tva,
        Zone zone,
        BigDecimal ppv,
        BigDecimal pph,
        BigDecimal tauxMarge,
        Integer stockMin,
        Integer stockMax,
        LocalDate datePeremption,
        boolean actif) {}
