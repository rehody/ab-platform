package io.github.rehody.abplatform.web.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class PlatformActorAuthenticationToken extends AbstractAuthenticationToken {

    @Getter
    private final UUID actorId;

    private final PlatformActor actor;

    private PlatformActorAuthenticationToken(
            UUID actorId, PlatformActor actor, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.actorId = actorId;
        this.actor = actor;
    }

    public static PlatformActorAuthenticationToken unauthenticated(UUID actorId) {
        PlatformActorAuthenticationToken authentication =
                new PlatformActorAuthenticationToken(actorId, null, List.of());
        authentication.setAuthenticated(false);
        return authentication;
    }

    public static PlatformActorAuthenticationToken authenticated(
            PlatformActor actor, Collection<? extends GrantedAuthority> authorities) {
        PlatformActorAuthenticationToken authentication =
                new PlatformActorAuthenticationToken(actor.actorId(), actor, authorities);
        authentication.setAuthenticated(true);
        return authentication;
    }

    @Override
    public Object getPrincipal() {
        if (actor != null) {
            return actor;
        }

        return actorId;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public @NonNull String getName() {
        return getActorId().toString();
    }
}
