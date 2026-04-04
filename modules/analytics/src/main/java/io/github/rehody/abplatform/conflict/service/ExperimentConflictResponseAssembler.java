package io.github.rehody.abplatform.conflict.service;

import io.github.rehody.abplatform.conflict.dto.response.ExperimentConflictListResponse;
import io.github.rehody.abplatform.conflict.dto.response.ExperimentConflictResponse;
import io.github.rehody.abplatform.conflict.enums.ConflictSeverity;
import io.github.rehody.abplatform.conflict.model.ExperimentConflict;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExperimentConflictResponseAssembler {

    public ExperimentConflictListResponse assemble(List<ExperimentConflict> conflicts) {
        ConflictSeverity severity = resolveSeverity(conflicts);

        List<ExperimentConflictResponse> conflictResponses =
                conflicts.stream().map(ExperimentConflictResponse::from).toList();

        return new ExperimentConflictListResponse(severity, conflictResponses);
    }

    private ConflictSeverity resolveSeverity(List<ExperimentConflict> conflicts) {
        ConflictSeverity severity = ConflictSeverity.NONE;

        if (conflicts.stream().anyMatch(conflict -> conflict.severity().isBlocking())) {
            severity = ConflictSeverity.BLOCKING;
        }

        if (!conflicts.isEmpty()) {
            severity = ConflictSeverity.WARNING;
        }

        return severity;
    }
}
