package io.github.rehody.abplatform.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.binding.policy.ExperimentMetricBindingConflictPolicy;
import io.github.rehody.abplatform.binding.policy.ExperimentMetricBindingPolicy;
import io.github.rehody.abplatform.binding.repository.ExperimentMetricBindingRepository;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.evaluation.policy.ExperimentMetricEvaluationPolicy;
import io.github.rehody.abplatform.exception.ExperimentActivationConflictException;
import io.github.rehody.abplatform.metric.enums.MetricDirection;
import io.github.rehody.abplatform.metric.enums.MetricSeverity;
import io.github.rehody.abplatform.metric.enums.MetricType;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.service.MetricDefinitionService;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.risk.enums.ExperimentMetricRiskStatus;
import io.github.rehody.abplatform.risk.model.ExperimentMetricRisk;
import io.github.rehody.abplatform.risk.policy.ExperimentMetricRiskPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsPolicyTest {

    @Mock
    private MetricDefinitionService metricDefinitionService;

    @Mock
    private ExperimentMetricBindingRepository experimentMetricBindingRepository;

    @Test
    void bindingPolicy_shouldNormalizeDeduplicateAndValidateCountableMetricKeys() {
        when(metricDefinitionService.getByKey("orders")).thenReturn(countableMetricDefinition("orders"));
        when(metricDefinitionService.getByKey("revenue")).thenReturn(countableMetricDefinition("revenue"));
        ExperimentMetricBindingPolicy policy = new ExperimentMetricBindingPolicy(metricDefinitionService);

        List<String> response = policy.prepareMetricKeys(List.of(" orders ", "revenue", "orders"));

        assertThat(response).containsExactly("orders", "revenue");
    }

    @Test
    void bindingPolicy_shouldRejectNullBlankAndUnsupportedMetrics() {
        when(metricDefinitionService.getByKey("signup")).thenReturn(uniqueMetricDefinition("signup"));
        ExperimentMetricBindingPolicy policy = new ExperimentMetricBindingPolicy(metricDefinitionService);

        assertThatThrownBy(() -> policy.prepareMetricKeys(Arrays.asList((String) null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metricKey must not be null");

        assertThatThrownBy(() -> policy.prepareMetricKeys(List.of("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metricKey must not be blank");

        assertThatThrownBy(() -> policy.prepareMetricKeys(List.of("signup")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Metric 'signup' is not supported by evaluation");
    }

    @Test
    void bindingConflictPolicy_shouldValidateActivationAndRejectConflicts() {
        UUID experimentId = UUID.randomUUID();
        Experiment experiment = new Experiment(
                experimentId, "flag-orders", "CHECKOUT", List.of(), ExperimentState.RUNNING, 3L, null, null);
        when(experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId))
                .thenReturn(List.of("orders"));
        when(experimentMetricBindingRepository.findConflictingMetricKeys(experimentId, List.of("orders")))
                .thenReturn(List.of());
        ExperimentMetricBindingConflictPolicy policy =
                new ExperimentMetricBindingConflictPolicy(experimentMetricBindingRepository);

        policy.validateActivation(experiment);

        verify(experimentMetricBindingRepository).findMetricKeysByExperimentId(experimentId);

        when(experimentMetricBindingRepository.findConflictingMetricKeys(experimentId, List.of("orders")))
                .thenReturn(List.of("orders", "revenue"));

        assertThatThrownBy(() -> policy.validateNoRunningMetricConflicts(experimentId, List.of("orders")))
                .isInstanceOf(ExperimentActivationConflictException.class)
                .hasMessageContaining("orders, revenue");
    }

    @Test
    void evaluationPolicy_shouldRequireBoundCountableMetric() {
        UUID experimentId = UUID.randomUUID();
        when(experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId))
                .thenReturn(List.of("orders", "signup"));
        when(metricDefinitionService.getByKey("orders")).thenReturn(countableMetricDefinition("orders"));
        when(metricDefinitionService.getByKey("signup")).thenReturn(uniqueMetricDefinition("signup"));
        ExperimentMetricEvaluationPolicy policy =
                new ExperimentMetricEvaluationPolicy(experimentMetricBindingRepository, metricDefinitionService);

        assertThat(policy.getMetricDefinitionForEvaluation(experimentId, "orders")
                        .key())
                .isEqualTo("orders");

        assertThatThrownBy(() -> policy.getMetricDefinitionForEvaluation(experimentId, "signup"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Metric 'signup' is not supported by evaluation");

        assertThatThrownBy(() -> policy.getMetricDefinitionForEvaluation(experimentId, "revenue"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Metric 'revenue' is not bound to experiment '%s'".formatted(experimentId));
    }

    @Test
    void riskPolicy_shouldConvertDeviationAndDetectWorsening() {
        ExperimentMetricRiskPolicy policy = new ExperimentMetricRiskPolicy();
        ExperimentMetricRisk risk = new ExperimentMetricRisk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "orders",
                UUID.randomUUID(),
                ExperimentMetricRiskStatus.OPEN,
                Instant.parse("2026-04-05T10:00:00Z"),
                null,
                null,
                Instant.parse("2026-04-05T10:05:00Z"),
                new BigDecimal("0.12"),
                new BigDecimal("0.20"),
                null);

        assertThat(policy.toBadDeviation(MetricDirection.MORE_IS_BETTER, new BigDecimal("-0.15")))
                .isEqualByComparingTo("0.15");
        assertThat(policy.toBadDeviation(MetricDirection.LESS_IS_BETTER, new BigDecimal("0.15")))
                .isEqualByComparingTo("0.15");
        assertThat(policy.isWorsening(risk, new BigDecimal("0.25"))).isTrue();
        assertThat(policy.isWorsening(risk, new BigDecimal("0.20"))).isFalse();
    }

    private MetricDefinition countableMetricDefinition(String key) {
        return new MetricDefinition(
                UUID.randomUUID(),
                key,
                key,
                MetricType.COUNTABLE,
                MetricDirection.MORE_IS_BETTER,
                MetricSeverity.HIGH,
                new BigDecimal("0.10"));
    }

    private MetricDefinition uniqueMetricDefinition(String key) {
        return new MetricDefinition(
                UUID.randomUUID(),
                key,
                key,
                MetricType.UNIQUE,
                MetricDirection.LESS_IS_BETTER,
                MetricSeverity.MEDIUM,
                new BigDecimal("0.20"));
    }
}
