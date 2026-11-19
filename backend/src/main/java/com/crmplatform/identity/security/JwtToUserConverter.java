package com.crmplatform.identity.security;

import com.crmplatform.identity.application.MembershipLookup;
import com.crmplatform.identity.application.TokenDenylist;
import com.crmplatform.identity.domain.Role;
import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.Permissions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Turns a validated JWT into an {@link AuthenticatedUser}. Role and permissions are re-read from
 * the (cached) membership rather than trusted from the token, so revocations apply at once.
 */
@Component
class JwtToUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final MembershipLookup membershipLookup;
    private final TokenDenylist denylist;

    JwtToUserConverter(MembershipLookup membershipLookup, TokenDenylist denylist) {
        this.membershipLookup = membershipLookup;
        this.denylist = denylist;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (denylist.isDenied(jwt.getId())) {
            throw new InvalidBearerTokenException("Token revoked");
        }
        UUID userId;
        UUID workspaceId;
        try {
            userId = UUID.fromString(jwt.getSubject());
            workspaceId = UUID.fromString(jwt.getClaimAsString("wid"));
        } catch (RuntimeException e) {
            throw new InvalidBearerTokenException("Malformed token");
        }
        boolean superAdmin = Boolean.TRUE.equals(jwt.getClaimAsBoolean("sa"));
        String role = MembershipLookup.asRole(membershipLookup.roleOf(userId, workspaceId)).orElse(null);

        Set<String> permissions = new HashSet<>();
        if (role != null) {
            permissions.addAll(Role.valueOf(role).permissions());
        }
        if (superAdmin) {
            permissions.add(Permissions.PLATFORM_ADMIN);
        }
        AuthenticatedUser user = new AuthenticatedUser(userId, workspaceId, role, Set.copyOf(permissions), superAdmin,
                jwt.getId(), jwt.getExpiresAt());
        List<SimpleGrantedAuthority> authorities = permissions.stream().map(SimpleGrantedAuthority::new).toList();
        return new CrmAuthenticationToken(user, jwt, authorities);
    }
}
