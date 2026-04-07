package io.github.rehody.abplatform.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.risk.enums.ExperimentMetricRiskStatus;
import io.github.rehody.abplatform.risk.model.ExperimentMetricRisk;
import io.github.rehody.abplatform.service.ExperimentLifecycleService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentMetricAutoPauseServiceTest {

    @Mock
    private ExperimentLifecycleService experimentLifecycleService;

    @Test
    void pause_shouldReturnCurrentTimestampWhenLifecyclePauseSucceeds() {
        Experiment experiment = new Experiment(
                UUID.randomUUID(), "flag-orders", "CHECKOUT", List.of(), ExperimentState.RUNNING, 4L, null, null);
        ExperimentMetricRisk risk = risk(null);
        ExperimentMetricAutoPauseService service = new ExperimentMetricAutoPauseService(experimentLifecycleService);

        Instant response = service.pause(experiment, risk);

        assertThat(response).isNotNull();
        verify(experimentLifecycleService).pause(experiment.id(), experiment.version());
    }

    @Test
    void pause_shouldReturnPreviousAutoPausedAtWhenLifecyclePauseFails() {
        Instant autoPausedAt = Instant.parse("2026-04-05T11:00:00Z");
        Experiment experiment = new Experiment(
                UUID.randomUUID(), "flag-orders", "CHECKOUT", List.of(), ExperimentState.RUNNING, 4L, null, null);
        ExperimentMetricRisk risk = risk(autoPausedAt);
        doThrow(new IllegalStateException("boom"))
                .when(experimentLifecycleService)
                .pause(experiment.id(), experiment.version());
        ExperimentMetricAutoPauseService service = new ExperimentMetricAutoPauseService(experimentLifecycleService);

        Instant response = service.pause(experiment, risk);

        assertThat(response).isEqualTo(autoPausedAt);
    }

    private ExperimentMetricRisk risk(Instant autoPausedAt) {
        return new ExperimentMetricRisk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "orders",
                UUID.randomUUID(),
                ExperimentMetricRiskStatus.OPEN,
                Instant.parse("2026-04-05T10:00:00Z"),
                null,
                null,
                Instant.parse("2026-04-05T10:05:00Z"),
                new BigDecimal("0.11"),
                new BigDecimal("0.15"),
                autoPausedAt);
    }
}
