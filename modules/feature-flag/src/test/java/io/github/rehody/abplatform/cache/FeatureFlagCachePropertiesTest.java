package io.github.rehody.abplatform.cache;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FeatureFlagCachePropertiesTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void validate_shouldReturnNoViolationsAndAcceptValidProperties() {
        FeatureFlagCacheProperties properties = validProperties();

        Set<ConstraintViolation<FeatureFlagCacheProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    void validate_shouldReturnViolationsAndRejectInvalidProperties() {
        FeatureFlagCacheProperties properties = new FeatureFlagCacheProperties();
        properties.setL1ValueSize(0);
        properties.setL1MissSize(-1);
        properties.setTtlSpread(1.2d);
        properties.setRedisKeyPrefix(" ");
        properties.setInvalidationTopic(" ");

        Set<ConstraintViolation<FeatureFlagCacheProperties>> violations = validator.validate(properties);
        Set<String> violationFields = violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(violationFields)
                .contains(
                        "l1ValueTtl",
                        "l1MissTtl",
                        "l1ValueSize",
                        "l1MissSize",
                        "l2ValueTtl",
                        "l2MissTtl",
                        "ttlSpread",
                        "redisKeyPrefix",
                        "invalidationTopic");
    }

    private FeatureFlagCacheProperties validProperties() {
        FeatureFlagCacheProperties properties = new FeatureFlagCacheProperties();
        properties.setL1ValueTtl(Duration.ofSeconds(30));
        properties.setL1MissTtl(Duration.ofSeconds(10));
        properties.setL1ValueSize(1000);
        properties.setL1MissSize(500);
        properties.setL2ValueTtl(Duration.ofMinutes(10));
        properties.setL2MissTtl(Duration.ofSeconds(30));
        properties.setTtlSpread(0.1d);
        properties.setRedisKeyPrefix("ab-platform:cache:feature-flag:v1:");
        properties.setInvalidationTopic("ab-platform:cache:feature-flag:v1:invalidation");
        return properties;
    }
}
