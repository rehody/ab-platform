package io.github.rehody.abplatform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.dto.request.ExperimentRolloutActionRequest;
import io.github.rehody.abplatform.dto.response.ExperimentRolloutResponse;
import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentRolloutPlan;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.service.ExperimentQueryService;
import io.github.rehody.abplatform.service.ExperimentRuntimeService;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentRolloutControllerTest {

    private static final String ACTOR_ID = "11111111-1111-1111-1111-111111111111";

    @Mock
    private ExperimentQueryService experimentQueryService;

    @Mock
    private ExperimentRuntimeService experimentRuntimeService;

    private ExperimentRolloutController experimentRolloutController;

    @BeforeEach
    void setUp() {
        experimentRolloutController = new ExperimentRolloutController(experimentQueryService, experimentRuntimeService);
    }

    @Test
    void get_shouldReturnMappedRolloutResponse() {
        Experiment experiment = experiment();
        when(experimentQueryService.getById(experiment.id())).thenReturn(experiment);

        ExperimentRolloutResponse response = experimentRolloutController.get(experiment.id());

        assertThat(response.version()).isEqualTo(experiment.version());
        verify(experimentQueryService).getById(experiment.id());
    }

    @Test
    void advance_shouldDelegateToRuntimeServiceAndMapResponse() {
        Experiment experiment = experiment();
        Principal principal = () -> ACTOR_ID;
        when(experimentRuntimeService.advanceRollout(
                        eq(experiment.id()), eq(3L), eq(AuditActor.user(UUID.fromString(ACTOR_ID)))))
                .thenReturn(experiment);

        ExperimentRolloutResponse response =
                experimentRolloutController.advance(principal, experiment.id(), new ExperimentRolloutActionRequest(3L));

        assertThat(response.version()).isEqualTo(experiment.version());
    }

    @Test
    void rollback_shouldDelegateToRuntimeServiceAndMapResponse() {
        Experiment experiment = experiment();
        Principal principal = () -> ACTOR_ID;
        when(experimentRuntimeService.rollbackRollout(
                        eq(experiment.id()), eq(3L), eq(AuditActor.user(UUID.fromString(ACTOR_ID)))))
                .thenReturn(experiment);

        ExperimentRolloutResponse response = experimentRolloutController.rollback(
                principal, experiment.id(), new ExperimentRolloutActionRequest(3L));

        assertThat(response.version()).isEqualTo(experiment.version());
    }

    private Experiment experiment() {
        return new Experiment(
                UUID.randomUUID(),
                "flag-orders",
                "CHECKOUT",
                ExperimentRolloutPlan.of(15, false, false),
                List.of(),
                ExperimentState.RUNNING,
                3L,
                null,
                null);
    }
}
