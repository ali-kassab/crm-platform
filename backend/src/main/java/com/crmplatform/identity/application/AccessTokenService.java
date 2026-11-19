package com.crmplatform.identity.application;

import com.crmplatform.identity.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived HS256 access tokens (ADR-0003). */
@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final IdentityProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, IdentityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(UUID userId, UUID workspaceId, Role role, boolean superAdmin) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        String jti = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(jti)
                .claim("wid", workspaceId.toString())
                .claim("role", role.name())
                .claim("perms", List.copyOf(role.permissions()))
                .claim("sa", superAdmin)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedAccessToken(token, expiresAt, properties.accessTokenTtl().toSeconds());
    }

    public record IssuedAccessToken(String value, Instant expiresAt, long expiresInSeconds) {
    }
}
