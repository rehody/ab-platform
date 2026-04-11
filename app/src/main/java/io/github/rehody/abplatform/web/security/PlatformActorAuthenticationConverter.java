package io.github.rehody.abplatform.web.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Component;

@Component
public class PlatformActorAuthenticationConverter implements AuthenticationConverter {

    private static final String ACTOR_ID_HEADER = "X-Actor-Id";

    @Override
    public Authentication convert(@NonNull HttpServletRequest request) {
        UUID actorId = resolveActorId(request);
        return PlatformActorAuthenticationToken.unauthenticated(actorId);
    }

    private UUID resolveActorId(HttpServletRequest request) {
        String actorIdHeader = request.getHeader(ACTOR_ID_HEADER);
        if (actorIdHeader == null || actorIdHeader.isBlank()) {
            throw new BadCredentialsException("Missing X-Actor-Id header");
        }

        try {
            return UUID.fromString(actorIdHeader.trim());
        } catch (IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid X-Actor-Id header", ex);
        }
    }
}
