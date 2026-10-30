package ma.invento.pos.importation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Une ligne de fichier lue et nettoyée, prête à être appliquée au catalogue. */
public record LigneCatalogue(
        int numeroLigne,
        String pid,
        String designation,
        String categorie,
        BigDecimal tauxMarge,
        String tvaLibelle,
        BigDecimal tvaTaux,
        String zone,
        BigDecimal ppv,
        BigDecimal pph,
        int stock,
        Integer stockMin,
        Integer stockMax,
        LocalDate datePeremption,
        List<String> codesBarres) {}
