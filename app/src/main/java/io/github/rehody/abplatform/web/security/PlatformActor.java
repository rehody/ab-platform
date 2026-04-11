package io.github.rehody.abplatform.web.security;

import io.github.rehody.abplatform.security.PlatformRole;
import java.security.Principal;
import java.util.UUID;

public record PlatformActor(UUID actorId, PlatformRole role) implements Principal {

    @Override
    public String getName() {
        return actorId.toString();
    }
}
