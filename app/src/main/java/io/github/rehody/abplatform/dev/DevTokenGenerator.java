package io.github.rehody.abplatform.dev;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.ObjectMapper;

public final class DevTokenGenerator {

    private static final String USAGE =
            "Usage: ./gradlew :app:runDevToken --args=\"<viewer|experimenter|operator> [--raw]\"";
    private static final String SHARED_SECRET_ENV = "AB_AUTH_INTERNAL_TOKEN_SHARED_SECRET";
    private static final String ISSUER_ENV = "AB_AUTH_INTERNAL_TOKEN_ISSUER";
    private static final String TTL_SECONDS_ENV = "AB_AUTH_INTERNAL_TOKEN_TTL_SECONDS";
    private static final String DEFAULT_ISSUER = "ab-platform-internal";
    private static final long DEFAULT_TTL_SECONDS = 3600L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();

    private static final Map<String, UUID> ACTOR_FIXTURES = Map.of(
            "viewer", UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "experimenter", UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "operator", UUID.fromString("33333333-3333-3333-3333-333333333333"));

    private DevTokenGenerator() {}

    static void main(String[] args) throws Exception {
        try {
            Arguments arguments = Arguments.parse(args);
            TokenSettings tokenSettings = TokenSettings.fromEnvironment();
            UUID actorId = resolveActorId(arguments.actorAlias());
            String token = createToken(actorId, tokenSettings);

            if (arguments.raw()) {
                System.out.println(token);
                return;
            }

            System.out.println("Bearer " + token);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }
    }

    private static UUID resolveActorId(String actorAlias) {
        UUID actorId = ACTOR_FIXTURES.get(actorAlias);
        if (actorId == null) {
            throw new IllegalArgumentException(USAGE);
        }
        return actorId;
    }

    private static String createToken(UUID actorId, TokenSettings tokenSettings) throws Exception {
        long issuedAt = Instant.now().getEpochSecond();
        String encodedHeader = encodeJson(createHeader());
        String encodedPayload = encodeJson(createPayload(actorId, issuedAt, tokenSettings));
        String signingInput = encodedHeader + "." + encodedPayload;
        return signingInput + "." + signHs256(signingInput, tokenSettings.sharedSecret());
    }

    private static Map<String, Object> createHeader() {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("typ", "JWT");
        header.put("alg", "HS256");
        return header;
    }

    private static Map<String, Object> createPayload(UUID actorId, long issuedAt, TokenSettings tokenSettings) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", tokenSettings.issuer());
        payload.put("actorId", actorId.toString());
        payload.put("iat", issuedAt);
        payload.put("exp", issuedAt + tokenSettings.ttlSeconds());
        return payload;
    }

    private static String encodeJson(Map<String, ?> value) {
        byte[] json = OBJECT_MAPPER.writeValueAsBytes(value);
        return BASE64_URL_ENCODER.encodeToString(json);
    }

    private static String signHs256(String signingInput, String sharedSecret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        return BASE64_URL_ENCODER.encodeToString(signature);
    }

    private record Arguments(String actorAlias, boolean raw) {

        private static Arguments parse(String[] args) {
            if (args.length < 1 || args.length > 2) {
                throw new IllegalArgumentException(USAGE);
            }

            String actorAlias = args[0];
            if (!ACTOR_FIXTURES.containsKey(actorAlias)) {
                throw new IllegalArgumentException(USAGE);
            }

            if (args.length == 1) {
                return new Arguments(actorAlias, false);
            }

            if (!"--raw".equals(args[1])) {
                throw new IllegalArgumentException(USAGE);
            }

            return new Arguments(actorAlias, true);
        }
    }

    private record TokenSettings(String issuer, String sharedSecret, long ttlSeconds) {

        private static TokenSettings fromEnvironment() {
            return new TokenSettings(resolveIssuer(), requireSharedSecret(), resolveTtlSeconds());
        }

        private static String requireSharedSecret() {
            String sharedSecret = System.getenv(SHARED_SECRET_ENV);
            if (sharedSecret == null || sharedSecret.isBlank()) {
                throw new IllegalArgumentException(SHARED_SECRET_ENV + " is required");
            }
            return sharedSecret;
        }

        private static String resolveIssuer() {
            String issuer = System.getenv(ISSUER_ENV);
            if (issuer == null || issuer.isBlank()) {
                return DEFAULT_ISSUER;
            }
            return issuer;
        }

        private static long resolveTtlSeconds() {
            String rawValue = System.getenv(TTL_SECONDS_ENV);
            if (rawValue == null || rawValue.isBlank()) {
                return DEFAULT_TTL_SECONDS;
            }

            try {
                long ttlSeconds = Long.parseLong(rawValue);
                if (ttlSeconds <= 0) {
                    throw new IllegalArgumentException(TTL_SECONDS_ENV + " must be positive");
                }
                return ttlSeconds;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(TTL_SECONDS_ENV + " must be an integer");
            }
        }
    }
}
