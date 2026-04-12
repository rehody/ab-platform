package io.github.rehody.abplatform.cache;

import io.github.rehody.abplatform.util.cache.TwoLevelCacheProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ab.cache.assignment-plan")
public class AssignmentPlanCacheProperties implements TwoLevelCacheProperties {
    @NotNull private Duration l1ValueTtl;

    @NotNull private Duration l1MissTtl;

    @Positive private long l1ValueSize;

    @Positive private long l1MissSize;

    @NotNull private Duration l2ValueTtl;

    @NotNull private Duration l2MissTtl;

    @DecimalMin("0.0") @DecimalMax("1.0") private double ttlSpread;

    @NotBlank private String redisKeyPrefix;

    @NotBlank private String invalidationTopic;
}
