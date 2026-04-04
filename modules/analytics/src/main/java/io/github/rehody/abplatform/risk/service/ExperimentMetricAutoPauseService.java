package io.github.rehody.abplatform.risk.service;

import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.risk.model.ExperimentMetricRisk;
import io.github.rehody.abplatform.service.ExperimentLifecycleService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentMetricAutoPauseService {

    private static final Logger log = LoggerFactory.getLogger(ExperimentMetricAutoPauseService.class);

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
