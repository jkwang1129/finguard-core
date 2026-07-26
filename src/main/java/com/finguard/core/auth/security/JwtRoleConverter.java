package com.finguard.core.auth.security;

import com.finguard.core.auth.model.RoleCode;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Component
public class JwtRoleConverter implements Converter<
        Jwt,
        AbstractOAuth2TokenAuthenticationToken<Jwt>> {

    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public AbstractOAuth2TokenAuthenticationToken<Jwt> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = extractRoles(jwt).stream()
                .map(role -> ROLE_PREFIX + role.name())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
        String principalName = principalName(jwt);
        return new JwtAuthenticationToken(
                jwt,
                authorities,
                principalName
        );
    }

    private List<RoleCode> extractRoles(Jwt jwt) {
        Object claim = jwt.getClaim(ROLES_CLAIM);
        if (!(claim instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(this::toKnownRole)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private RoleCode toKnownRole(String value) {
        try {
            return RoleCode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String principalName(Jwt jwt) {
        String username = jwt.getClaimAsString("username");
        return StringUtils.hasText(username)
                ? username
                : jwt.getSubject();
    }
}
