package ma.invento.pos.identite.application;

import java.time.Clock;
import java.time.Instant;
import ma.invento.pos.identite.config.SecurityProperties;
import ma.invento.pos.identite.domain.GenerateurSecrets;
import ma.invento.pos.identite.domain.RefreshToken;
import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.identite.infrastructure.RefreshTokenRepository;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Émet la paire jeton d'accès (JWT court) + jeton de rafraîchissement (opaque, stocké haché). */
@Service
public class JetonService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_LOGIN = "login";
    public static final String CLAIM_MDP_A_CHANGER = "mdp_a_changer";
    private static final String EMETTEUR = "invento-pos";

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final GenerateurSecrets generateur;
    private final SecurityProperties properties;
    private final Clock clock;

    public JetonService(
            JwtEncoder jwtEncoder,
            RefreshTokenRepository refreshTokens,
            GenerateurSecrets generateur,
            SecurityProperties properties,
            Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
        this.generateur = generateur;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Jetons emettre(Utilisateur utilisateur) {
        Instant maintenant = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMETTEUR)
                .subject(String.valueOf(utilisateur.getId()))
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(properties.accessTokenTtl()))
                .claim(CLAIM_LOGIN, utilisateur.getLogin())
                .claim(CLAIM_ROLE, utilisateur.getRole().name())
                .claim(CLAIM_MDP_A_CHANGER, utilisateur.isDoitChangerMotDePasse())
                .build();
        String acces = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        String rafraichissement = generateur.jetonRafraichissement();
        refreshTokens.save(new RefreshToken(
                utilisateur.getId(),
                GenerateurSecrets.empreinte(rafraichissement),
                maintenant,
                maintenant.plus(properties.refreshTokenTtl())));
        return new Jetons(acces, properties.accessTokenTtl().toSeconds(), rafraichissement);
    }

    /** @param rafraichissement jeton opaque, à placer uniquement dans un cookie HttpOnly */
    public record Jetons(String acces, long expireDansSecondes, String rafraichissement) {}
}
