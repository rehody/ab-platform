package io.github.rehody.abplatform.conflict.controller;

import io.github.rehody.abplatform.conflict.dto.response.ExperimentConflictListResponse;
import io.github.rehody.abplatform.conflict.model.ExperimentConflict;
import io.github.rehody.abplatform.conflict.service.ExperimentConflictResponseAssembler;
import io.github.rehody.abplatform.conflict.service.ExperimentConflictsService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experiments")
@RequiredArgsConstructor
public class ExperimentConflictsController {

    private final ExperimentConflictsService experimentConflictService;
    private final ExperimentConflictResponseAssembler experimentConflictResponseAssembler;

    @GetMapping("/{id}/conflicts")
    public ExperimentConflictListResponse getConflicts(@PathVariable UUID id) {
        List<ExperimentConflict> conflicts = experimentConflictService.getAll(id);
        return experimentConflictResponseAssembler.assemble(conflicts);
    }
}
