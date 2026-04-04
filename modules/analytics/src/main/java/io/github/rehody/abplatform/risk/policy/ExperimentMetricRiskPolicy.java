package io.github.rehody.abplatform.risk.policy;

import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.risk.model.ExperimentMetricRisk;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class ExperimentMetricRiskPolicy {

    public boolean isWorsening(ExperimentMetricRisk currentRisk, BigDecimal badDeviation) {
        return badDeviation.compareTo(currentRisk.worstBadDeviation()) > 0;
    }

    public BigDecimal toBadDeviation(MetricDirection metricDirection, BigDecimal relativeDeviation) {
        if (metricDirection == MetricDirection.MORE_IS_BETTER) {
            return relativeDeviation.negate();
        }

        return relativeDeviation;
    }
}
