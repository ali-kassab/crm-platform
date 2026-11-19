package com.crmplatform.identity.security;

import com.crmplatform.shared.security.AuthenticatedUser;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Authentication whose principal is the resolved {@link AuthenticatedUser}. */
public class CrmAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser principal;
    private final transient Jwt jwt;

    public CrmAuthenticationToken(AuthenticatedUser principal, Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.userId().toString();
    }
}
