package io.github.rehody.abplatform.conflict.dto.response;

import io.github.rehody.abplatform.conflict.enums.ConflictSeverity;
import java.util.List;

public record ExperimentConflictListResponse(ConflictSeverity status, List<ExperimentConflictResponse> conflicts) {}
