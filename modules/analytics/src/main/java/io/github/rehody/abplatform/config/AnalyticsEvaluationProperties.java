package io.github.rehody.abplatform.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Component
@Validated
@ConfigurationProperties(prefix = "ab.analytics.evaluation")
public class AnalyticsEvaluationProperties {

    @NotNull private Duration evaluationInterval = Duration.ofMinutes(15);

    @Min(1) private int minimumEventsForAnalysis = 300;

    @Min(1) private int minimumParticipantsForTrafficWarning = 300;

    @NotNull @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") private BigDecimal trafficShareWarningThreshold = new BigDecimal("0.1");
}
