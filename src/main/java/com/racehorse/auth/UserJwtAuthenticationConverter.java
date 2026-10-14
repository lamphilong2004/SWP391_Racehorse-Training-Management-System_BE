package com.racehorse.auth;

import com.racehorse.identity.AppUser;
import com.racehorse.identity.AppUserRepository;
import com.racehorse.identity.RoleCode;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a valid JWT into an authenticated request. The user is re-read from the database: a deactivated user,
 * a user without a password hash or a deleted id gets 401 even with an unexpired token, and authorities always
 * reflect the current user_role rows.
 */
@Component
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AppUserRepository users;

    public UserJwtAuthenticationConverter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidBearerTokenException("Invalid token subject.");
        }
        AppUser user = users.findWithRolesById(userId)
                .filter(AppUser::canAuthenticate)
                .orElseThrow(() -> new InvalidBearerTokenException("The account can no longer sign in."));
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getUserCode(), user.roleCodes());
        var authorities = principal.roles().stream().map(RoleCode::authority).map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
    }
}
