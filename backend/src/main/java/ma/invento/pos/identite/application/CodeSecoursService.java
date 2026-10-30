package ma.invento.pos.identite.application;

import java.time.Clock;
import java.time.Instant;
import ma.invento.pos.identite.domain.CodeSecours;
import ma.invento.pos.identite.domain.GenerateurSecrets;
import ma.invento.pos.identite.domain.PolitiqueSecrets;
import ma.invento.pos.identite.infrastructure.CodeSecoursRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Code de secours de l'admin : un seul valide à la fois, haché, à usage unique. */
@Service
public class CodeSecoursService {

    private final CodeSecoursRepository codes;
    private final GenerateurSecrets generateur;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public CodeSecoursService(
            CodeSecoursRepository codes, GenerateurSecrets generateur, PasswordEncoder encoder, Clock clock) {
        this.codes = codes;
        this.generateur = generateur;
        this.encoder = encoder;
        this.clock = clock;
    }

    /** Remplace le code en vigueur et renvoie le nouveau code en clair, à montrer une seule fois. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String renouveler() {
        codes.supprimerCodesNonUtilises();
        String code = generateur.codeSecours();
        codes.save(new CodeSecours(encoder.encode(PolitiqueSecrets.normaliserCodeSecours(code)), Instant.now(clock)));
        return code;
    }

    /** Consomme le code s'il est valide ; renvoie faux sinon. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean consommer(String codeSaisi) {
        String normalise = PolitiqueSecrets.normaliserCodeSecours(codeSaisi);
        if (normalise.isEmpty()) return false;
        for (CodeSecours code : codes.findByUtiliseLeIsNull()) {
            if (encoder.matches(normalise, code.getCodeHash())) {
                code.marquerUtilise(Instant.now(clock));
                return true;
            }
        }
        return false;
    }
}
