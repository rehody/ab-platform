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

class AssignmentPlanCachePropertiesTest {

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
    void validate_shouldAcceptValidProperties() {
        AssignmentPlanCacheProperties properties = validProperties();

        Set<ConstraintViolation<AssignmentPlanCacheProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    void validate_shouldRejectInvalidProperties() {
        AssignmentPlanCacheProperties properties = new AssignmentPlanCacheProperties();
        properties.setL1ValueSize(0);
        properties.setL1MissSize(-1);
        properties.setTtlSpread(1.2d);
        properties.setRedisKeyPrefix(" ");
        properties.setInvalidationTopic(" ");

        Set<ConstraintViolation<AssignmentPlanCacheProperties>> violations = validator.validate(properties);
        Set<String> fields = violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(fields)
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

    private AssignmentPlanCacheProperties validProperties() {
        AssignmentPlanCacheProperties properties = new AssignmentPlanCacheProperties();
        properties.setL1ValueTtl(Duration.ofSeconds(30));
        properties.setL1MissTtl(Duration.ofSeconds(10));
        properties.setL1ValueSize(1000);
        properties.setL1MissSize(500);
        properties.setL2ValueTtl(Duration.ofMinutes(10));
        properties.setL2MissTtl(Duration.ofSeconds(30));
        properties.setTtlSpread(0.1d);
        properties.setRedisKeyPrefix("ab-platform:cache:assignment-plan:v2:");
        properties.setInvalidationTopic("ab-platform:cache:assignment-plan:v2:invalidation");
        return properties;
    }
}
