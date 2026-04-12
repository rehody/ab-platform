package io.github.rehody.abplatform.risk.service;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.risk.model.ExperimentMetricRisk;
import io.github.rehody.abplatform.service.ExperimentLifecycleService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExperimentMetricAutoPauseService {

    private final ExperimentLifecycleService experimentLifecycleService;

    public Instant pause(Experiment experiment, ExperimentMetricRisk risk) {
        try {
            experimentLifecycleService.pause(experiment.id(), experiment.version());
            return Instant.now();
        } catch (RuntimeException ex) {
            log.warn("Failed to auto-pause experiment {} for risk {}: {}", experiment.id(), risk.id(), ex.getMessage());
            return risk.autoPausedAt();
        }
    }
}
