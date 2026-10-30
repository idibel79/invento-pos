package ma.invento.pos.identite.application;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
import ma.invento.pos.journal.application.JournalService;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autorisation d'une remise sur la caisse par un responsable ou l'admin, avec son PIN (cadrage
 * § 1). Le PIN est vérifié une fois ; la caisse reçoit un jeton signé, court et lié au caissier
 * demandeur, qu'elle joint à la vente. Ce jeton est signé avec une clé dérivée, distincte de
 * celle des jetons d'accès : il ne peut pas servir à appeler l'API.
 */
@Service
public class AutorisationService {

    public static final Duration DUREE = Duration.ofMinutes(15);
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_DEMANDEUR = "demandeur";
    private static final String PIN_REFUSE = "PIN incorrect.";

    private final UtilisateurRepository utilisateurs;
    private final PasswordEncoder encoder;
    private final JournalService journal;
    private final Clock clock;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public AutorisationService(
            UtilisateurRepository utilisateurs,
            PasswordEncoder encoder,
            JournalService journal,
            Clock clock,
            SecretKey jwtSecretKey) {
        this.utilisateurs = utilisateurs;
        this.encoder = encoder;
        this.journal = journal;
        this.clock = clock;
        SecretKey cle = deriver(jwtSecretKey);
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(cle));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(cle)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Échéance vérifiée avec l'horloge injectée (voir verifier).
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        this.jwtDecoder = decoder;
    }

    @Transactional(readOnly = true)
    public List<Autorisateur> autorisateurs() {
        return utilisateurs.findAutorisateurs(List.of(Role.RESPONSABLE, Role.ADMIN)).stream()
                .filter(u -> !u.isVerrouille())
                .map(u -> new Autorisateur(u.getId(), u.getPrenom(), u.getNom(), u.getRole()))
                .toList();
    }

    /** Les essais ratés sont comptés même si l'autorisation échoue ; 5 échecs verrouillent le compte. */
    @Transactional(noRollbackFor = {PinIncorrectException.class, CompteVerrouilleException.class})
    public JetonAutorisation autoriser(long autorisateurId, String pin, long demandeurId) {
        Utilisateur autorisateur = utilisateurs
                .findById(autorisateurId)
                .filter(u -> u.isActif() && u.isPinDefini() && u.getRole() != Role.CAISSIER)
                .orElseThrow(() -> new PinIncorrectException(PIN_REFUSE));
        if (autorisateur.isVerrouille()) {
            throw new CompteVerrouilleException();
        }
        if (!encoder.matches(pin, autorisateur.getPinHash())) {
            utilisateurs.incrementerEssaisPinRates(autorisateurId);
            if (autorisateur.getEssaisPinRates() + 1 >= Utilisateur.ESSAIS_MAX) {
                journal.enregistrer(autorisateurId, ActionJournal.COMPTE_VERROUILLE, "Trop d'essais de PIN ratés");
            }
            throw new PinIncorrectException(PIN_REFUSE);
        }
        autorisateur.pinReussi();
        Instant maintenant = Instant.now(clock);
        Instant expiration = maintenant.plus(DUREE);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(autorisateurId))
                .issuedAt(maintenant)
                .expiresAt(expiration)
                .claim(CLAIM_ROLE, autorisateur.getRole().name())
                .claim(CLAIM_DEMANDEUR, demandeurId)
                .build();
        String jeton = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new JetonAutorisation(
                jeton,
                autorisateurId,
                autorisateur.getPrenom() + " " + autorisateur.getNom(),
                autorisateur.getRole(),
                expiration);
    }

    /** Vérifie un jeton joint à une vente : signature, échéance et caissier demandeur. */
    public Autorisation verifier(String jeton, long demandeurId) {
        try {
            Jwt jwt = jwtDecoder.decode(jeton);
            Instant expiration = jwt.getExpiresAt();
            Object demandeur = jwt.getClaims().get(CLAIM_DEMANDEUR);
            if (expiration == null
                    || !expiration.isAfter(Instant.now(clock))
                    || !(demandeur instanceof Number n)
                    || n.longValue() != demandeurId) {
                throw new RegleMetierException("Autorisation expirée : faites ressaisir le PIN.");
            }
            // L'autorisateur doit être encore actif, non verrouillé et du même rôle qu'à la saisie du PIN.
            long autorisateurId = Long.parseLong(jwt.getSubject());
            Role role = Role.valueOf(Objects.requireNonNull(jwt.getClaimAsString(CLAIM_ROLE)));
            utilisateurs
                    .findById(autorisateurId)
                    .filter(u -> u.isActif() && !u.isVerrouille() && u.getRole() == role)
                    .orElseThrow(() -> new RegleMetierException("Autorisation retirée : faites ressaisir le PIN."));
            return new Autorisation(autorisateurId, role);
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new RegleMetierException("Autorisation invalide : faites ressaisir le PIN.");
        }
    }

    private static SecretKey deriver(SecretKey cle) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(cle);
            byte[] derivee = mac.doFinal("invento-pos/autorisation-remise".getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derivee, "HmacSHA256");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Dérivation de la clé d'autorisation impossible", e);
        }
    }

    public record Autorisateur(Long id, String prenom, String nom, Role role) {}

    public record JetonAutorisation(
            String jeton, Long autorisateurId, String autorisateur, Role role, Instant expireLe) {}

    public record Autorisation(long autorisateurId, Role role) {}
}
