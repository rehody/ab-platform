package io.github.rehody.abplatform.web.security;

import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class PlatformActorIdJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final String ACTOR_ID_CLAIM = "actorId";

    private static final OAuth2Error MISSING_ACTOR_ID_ERROR =
            new OAuth2Error("invalid_token", "Missing actorId claim", null);

    private static final OAuth2Error INVALID_ACTOR_ID_ERROR =
            new OAuth2Error("invalid_token", "Invalid actorId claim", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String actorId = token.getClaimAsString(ACTOR_ID_CLAIM);
        if (actorId == null || actorId.isBlank()) {
            return OAuth2TokenValidatorResult.failure(MISSING_ACTOR_ID_ERROR);
        }

        try {
            UUID.fromString(actorId);
            return OAuth2TokenValidatorResult.success();
        } catch (IllegalArgumentException ex) {
            return OAuth2TokenValidatorResult.failure(INVALID_ACTOR_ID_ERROR);
        }
    }
}
