package io.github.rehody.abplatform.web.security;

import io.github.rehody.abplatform.config.PlatformAuthorizationProperties;
import io.github.rehody.abplatform.security.PlatformPermission;
import io.github.rehody.abplatform.security.PlatformRole;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlatformJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ACTOR_ID_CLAIM = "actorId";
    private static final OAuth2Error INVALID_ACTOR_ID_ERROR =
            new OAuth2Error("invalid_token", "Invalid actorId claim", null);

    private final PlatformAuthorizationProperties platformAuthorizationProperties;

    @Override
    public Collection<GrantedAuthority> convert(@NonNull Jwt jwt) {
        UUID actorId = parseActorId(jwt);
        PlatformRole role = platformAuthorizationProperties.getActors().get(actorId);
        if (role == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "invalid_token", "Actor '%s' has no assigned global role".formatted(actorId), null));
        }

        return Arrays.stream(PlatformPermission.values())
                .filter(permission -> permission.isAllowedFor(role))
                .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission.name()))
                .toList();
    }

    private UUID parseActorId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getClaimAsString(ACTOR_ID_CLAIM));
        } catch (RuntimeException ex) {
            throw new OAuth2AuthenticationException(INVALID_ACTOR_ID_ERROR, ex);
        }
    }
}
