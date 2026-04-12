package io.github.rehody.abplatform.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.repository.ExperimentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentFeatureFlagUpdatePolicyTest {

    @Mock
    private ExperimentRepository experimentRepository;

    private ExperimentFeatureFlagUpdatePolicy experimentFeatureFlagUpdatePolicy;

    @BeforeEach
    void setUp() {
        experimentFeatureFlagUpdatePolicy = new ExperimentFeatureFlagUpdatePolicy(experimentRepository);
    }

    @Test
    void canUpdateDefaultValue_shouldReturnFalseWhenExperimentExists() {
        when(experimentRepository.existsByFlagKey("flag-a")).thenReturn(true);

        assertThat(experimentFeatureFlagUpdatePolicy.canUpdateDefaultValue("flag-a"))
                .isFalse();
    }

    @Test
    void canUpdateDefaultValue_shouldReturnTrueWhenExperimentDoesNotExist() {
        when(experimentRepository.existsByFlagKey("flag-b")).thenReturn(false);

        assertThat(experimentFeatureFlagUpdatePolicy.canUpdateDefaultValue("flag-b"))
                .isTrue();
    }
}
