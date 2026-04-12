package io.github.rehody.abplatform.conflict.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.conflict.enums.ConflictSeverity;
import io.github.rehody.abplatform.enums.ExperimentState;
import org.junit.jupiter.api.Test;

class ExperimentConflictSeverityResolverTest {

    private final ExperimentConflictSeverityResolver resolver = new ExperimentConflictSeverityResolver();

    @Test
    void resolve_shouldMapExperimentStatesToConflictSeverity() {
        assertThat(resolver.resolve(ExperimentState.RUNNING)).isEqualTo(ConflictSeverity.BLOCKING);
        assertThat(resolver.resolve(ExperimentState.DRAFT)).isEqualTo(ConflictSeverity.WARNING);
        assertThat(resolver.resolve(ExperimentState.REJECTED)).isEqualTo(ConflictSeverity.WARNING);
        assertThat(resolver.resolve(ExperimentState.IN_REVIEW)).isEqualTo(ConflictSeverity.WARNING);
        assertThat(resolver.resolve(ExperimentState.APPROVED)).isEqualTo(ConflictSeverity.WARNING);
        assertThat(resolver.resolve(ExperimentState.PAUSED)).isEqualTo(ConflictSeverity.WARNING);
        assertThat(resolver.resolve(ExperimentState.COMPLETED)).isEqualTo(ConflictSeverity.NONE);
        assertThat(resolver.resolve(ExperimentState.ARCHIVED)).isEqualTo(ConflictSeverity.NONE);
    }
}
