package ma.invento.pos.tresorerie.domain;

import java.math.BigDecimal;
import java.util.Objects;
import ma.invento.pos.shared.domain.RegleMetierException;

/**
 * Solde dû à la caisse principale réparti entre espèces et tickets TPE. Le dû TPE vient des encaissements
 * par TPE (ventes par carte, dettes remboursées par TPE) moins les tickets TPE déjà versés ; il ne dépasse
 * jamais le solde. Le reste du solde est dû en espèces (fond, excédents et ajustements compris).
 */
public record DuParMode(BigDecimal especes, BigDecimal tpe) {

    public DuParMode {
        Objects.requireNonNull(especes);
        Objects.requireNonNull(tpe);
    }

    /** {@code tpeNonVerses} : tickets TPE encaissés et pas encore versés, éventuellement hors bornes. */
    public static DuParMode depuis(BigDecimal solde, BigDecimal tpeNonVerses) {
        BigDecimal tpe = tpeNonVerses.min(solde).max(BigDecimal.ZERO);
        return new DuParMode(solde.subtract(tpe), tpe);
    }

    public BigDecimal total() {
        return especes.add(tpe);
    }

    /** Un versement ne dépasse, dans aucun mode, ce qui est dû dans ce mode. */
    public void verifierVersement(BigDecimal versementEspeces, BigDecimal versementTpe) {
        if (versementEspeces.compareTo(especes.max(BigDecimal.ZERO)) > 0) {
            throw new RegleMetierException("Le versement en espèces dépasse le dû en espèces ("
                    + especes.max(BigDecimal.ZERO).toPlainString() + " DH).");
        }
        if (versementTpe.compareTo(tpe) > 0) {
            throw new RegleMetierException(
                    "Le versement en tickets TPE dépasse le dû en TPE (" + tpe.toPlainString() + " DH).");
        }
    }
}
