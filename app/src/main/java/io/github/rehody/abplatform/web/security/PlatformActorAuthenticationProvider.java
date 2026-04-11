package io.github.rehody.abplatform.web.security;

import io.github.rehody.abplatform.config.PlatformAuthorizationProperties;
import io.github.rehody.abplatform.security.PlatformPermission;
import io.github.rehody.abplatform.security.PlatformRole;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlatformActorAuthenticationProvider implements AuthenticationProvider {

    private final PlatformAuthorizationProperties platformAuthorizationProperties;

    @Override
    public Authentication authenticate(@NonNull Authentication authentication) {
        UUID actorId = getActorId(authentication);
        PlatformActor actor = findRole(actorId)
                .map(role -> new PlatformActor(actorId, role))
                .orElseThrow(
                        () -> new BadCredentialsException("Actor '%s' has no assigned global role".formatted(actorId)));

        return PlatformActorAuthenticationToken.authenticated(actor, buildAuthorities(actor));
    }

    @Override
    public boolean supports(@NonNull Class<?> authentication) {
        return PlatformActorAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private List<SimpleGrantedAuthority> buildAuthorities(PlatformActor actor) {
        return Arrays.stream(PlatformPermission.values())
                .filter(permission -> permission.isAllowedFor(actor.role()))
                .map(permission -> new SimpleGrantedAuthority(permission.name()))
                .toList();
    }

    private UUID getActorId(Authentication authentication) {
        if (!(authentication instanceof PlatformActorAuthenticationToken token)) {
            throw new AuthenticationServiceException("Unsupported authentication token");
        }

        return token.getActorId();
    }

    private Optional<PlatformRole> findRole(UUID actorId) {
        return Optional.ofNullable(platformAuthorizationProperties.getActors().get(actorId));
    }
}
