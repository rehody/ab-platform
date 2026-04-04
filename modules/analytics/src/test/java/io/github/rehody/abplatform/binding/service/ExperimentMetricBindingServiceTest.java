package io.github.rehody.abplatform.binding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.binding.policy.ExperimentMetricBindingConflictPolicy;
import io.github.rehody.abplatform.binding.policy.ExperimentMetricBindingPolicy;
import io.github.rehody.abplatform.binding.repository.ExperimentMetricBindingRepository;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.service.ExperimentService;
import io.github.rehody.abplatform.service.ServiceActionExecutor;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentMetricBindingServiceTest {

    @Mock
    private ExperimentMetricBindingRepository experimentMetricBindingRepository;

    @Mock
    private ExperimentService experimentService;

    @Mock
    private ExperimentMetricBindingPolicy experimentMetricBindingPolicy;

    @Mock
    private ExperimentMetricBindingConflictPolicy experimentMetricBindingConflictPolicy;

    @Mock
    private ExperimentMetricBindingCacheInvalidator experimentMetricBindingCacheInvalidator;

    @Mock
    private ServiceActionExecutor serviceActionExecutor;

    private ExperimentMetricBindingService experimentMetricBindingService;

    @BeforeEach
    void setUp() {
        experimentMetricBindingService = new ExperimentMetricBindingService(
                experimentMetricBindingRepository,
                experimentService,
                experimentMetricBindingPolicy,
                experimentMetricBindingConflictPolicy,
                experimentMetricBindingCacheInvalidator,
                serviceActionExecutor);
    }

    @Test
    void updateMetricKeys_shouldInvalidatePreviousAndCurrentMetricReportsAfterCommit() {
        UUID experimentId = UUID.randomUUID();
        List<String> requestedMetricKeys = List.of("metric-shared", "metric-new");
        List<String> normalizedMetricKeys = List.of("metric-shared", "metric-new");
        Experiment experiment =
                new Experiment(experimentId, "checkout-redesign", List.of(), ExperimentState.DRAFT, 2L, null, null);
        when(experimentService.getById(experimentId)).thenReturn(experiment);
        when(experimentMetricBindingPolicy.prepareMetricKeys(requestedMetricKeys))
                .thenReturn(normalizedMetricKeys);
        when(experimentMetricBindingRepository.findMetricKeysByExperimentId(experimentId))
                .thenReturn(List.of("metric-old", "metric-shared"));

        List<String> response = experimentMetricBindingService.updateMetricKeys(experimentId, requestedMetricKeys);

        assertThat(response).isEqualTo(normalizedMetricKeys);
        verify(experimentMetricBindingRepository).updateMetricKeys(experimentId, normalizedMetricKeys);

        ArgumentCaptor<Runnable> afterCommitAction = ArgumentCaptor.forClass(Runnable.class);
        verify(serviceActionExecutor).executeAfterCommit(afterCommitAction.capture());

        afterCommitAction.getValue().run();

        verify(experimentMetricBindingCacheInvalidator)
                .invalidateReports(experimentId, List.of("metric-old", "metric-shared", "metric-new"));
    }
}
