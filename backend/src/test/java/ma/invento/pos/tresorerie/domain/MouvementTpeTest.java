package ma.invento.pos.tresorerie.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import ma.invento.pos.tresorerie.domain.MouvementTpe.Nature;
import org.junit.jupiter.api.Test;

class MouvementTpeTest {

    private static MouvementTpe encaissement(String montant) {
        return new MouvementTpe(Nature.ENCAISSEMENT, new BigDecimal(montant));
    }

    private static MouvementTpe versement(String montant) {
        return new MouvementTpe(Nature.VERSEMENT, new BigDecimal(montant));
    }

    @Test
    void un_versement_sans_repartition_emporte_d_abord_les_tickets_tpe() {
        // Ventes carte 95, versement global de 527 (session entière), puis nouvelle vente carte de 18,20.
        assertThat(MouvementTpe.nonVerses(List.of(encaissement("95.00"), versement("527.00"), encaissement("18.20"))))
                .isEqualByComparingTo("18.20");
    }

    @Test
    void un_versement_tpe_reduit_les_tickets_detenus() {
        assertThat(MouvementTpe.nonVerses(List.of(encaissement("140.00"), encaissement("20.00"), versement("100.00"))))
                .isEqualByComparingTo("60.00");
        assertThat(MouvementTpe.nonVerses(List.of())).isEqualByComparingTo("0");
    }
}
