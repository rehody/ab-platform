package io.github.rehody.abplatform.conflict.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rehody.abplatform.conflict.dto.response.ExperimentConflictListResponse;
import io.github.rehody.abplatform.conflict.enums.ConflictSeverity;
import io.github.rehody.abplatform.conflict.enums.ExperimentConflictType;
import io.github.rehody.abplatform.conflict.model.ExperimentConflict;
import io.github.rehody.abplatform.enums.ExperimentState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentConflictResponseAssemblerTest {

    private final ExperimentConflictResponseAssembler assembler = new ExperimentConflictResponseAssembler();

    @Test
    void assemble_shouldReturnWarningStatusWhenOnlyWarningConflictsExist() {
        ExperimentConflict conflict = new ExperimentConflict(
                UUID.randomUUID(),
                ExperimentState.DRAFT,
                "flag-a",
                "CHECKOUT",
                List.of(ExperimentConflictType.SAME_FLAG),
                ConflictSeverity.WARNING);

        ExperimentConflictListResponse response = assembler.assemble(List.of(conflict));

        assertThat(response.status()).isEqualTo(ConflictSeverity.WARNING);
        assertThat(response.conflicts()).hasSize(1);
        assertThat(response.conflicts().getFirst().state()).isEqualTo(ExperimentState.DRAFT);
        assertThat(response.conflicts().getFirst().severity()).isEqualTo(ConflictSeverity.WARNING);
    }

    @Test
    void assemble_shouldReturnBlockingStatusWhenBlockingConflictExists() {
        ExperimentConflict conflict = new ExperimentConflict(
                UUID.randomUUID(),
                ExperimentState.RUNNING,
                "flag-a",
                "CHECKOUT",
                List.of(ExperimentConflictType.SAME_FLAG),
                ConflictSeverity.BLOCKING);

        ExperimentConflictListResponse response = assembler.assemble(List.of(conflict));

        assertThat(response.status()).isEqualTo(ConflictSeverity.BLOCKING);
    }

    @Test
    void assemble_shouldReturnNoneStatusWhenConflictsAreEmpty() {
        ExperimentConflictListResponse response = assembler.assemble(List.of());

        assertThat(response.status()).isEqualTo(ConflictSeverity.NONE);
        assertThat(response.conflicts()).isEmpty();
    }
}
