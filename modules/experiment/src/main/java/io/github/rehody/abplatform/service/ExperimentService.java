package io.github.rehody.abplatform.service;

import io.github.rehody.abplatform.dto.request.ExperimentCreateRequest;
import io.github.rehody.abplatform.dto.request.ExperimentUpdateRequest;
import io.github.rehody.abplatform.dto.response.ExperimentResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExperimentService {

    public ExperimentResponse create(ExperimentCreateRequest request) {
        return null; // TODO
    }

    public ExperimentResponse update(UUID id, ExperimentUpdateRequest request) {
        return null; // TODO
    }

    public ExperimentResponse getById(UUID id) {
        return null; // TODO
    }

    public List<ExperimentResponse> getAll() {
        return null; // TODO
    }
}
