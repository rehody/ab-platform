package io.github.rehody.abplatform.metric.controller;

import io.github.rehody.abplatform.metric.dto.request.MetricDefinitionCreateRequest;
import io.github.rehody.abplatform.metric.dto.request.MetricDefinitionUpdateRequest;
import io.github.rehody.abplatform.metric.dto.response.MetricDefinitionResponse;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.service.MetricDefinitionService;
import io.github.rehody.abplatform.model.audit.AuditActor;
import io.github.rehody.abplatform.security.PlatformPermission;
import io.github.rehody.abplatform.security.RequiresPlatformPermission;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricDefinitionController {

    private final MetricDefinitionService metricDefinitionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPlatformPermission(PlatformPermission.CREATE_METRIC_DEFINITION)
    public MetricDefinitionResponse create(
            Principal principal, @Valid @RequestBody MetricDefinitionCreateRequest request) {
        MetricDefinition metricDefinition = metricDefinitionService.create(
                AuditActor.user(principal.getName()),
                request.key(),
                request.name(),
                request.type(),
                request.direction(),
                request.severity(),
                request.deviationThreshold());

        return MetricDefinitionResponse.from(metricDefinition);
    }

    @PutMapping("/{key}")
    @RequiresPlatformPermission(PlatformPermission.UPDATE_METRIC_DEFINITION)
    public MetricDefinitionResponse update(
            Principal principal, @PathVariable String key, @Valid @RequestBody MetricDefinitionUpdateRequest request) {
        MetricDefinition metricDefinition = metricDefinitionService.update(
                AuditActor.user(principal.getName()),
                key,
                request.name(),
                request.type(),
                request.direction(),
                request.severity(),
                request.deviationThreshold());

        return MetricDefinitionResponse.from(metricDefinition);
    }

    @GetMapping("/{key}")
    @RequiresPlatformPermission(PlatformPermission.VIEW_METRIC_DEFINITIONS)
    public MetricDefinitionResponse getByKey(@PathVariable String key) {
        MetricDefinition metricDefinition = metricDefinitionService.getByKey(key);
        return MetricDefinitionResponse.from(metricDefinition);
    }

    @GetMapping
    @RequiresPlatformPermission(PlatformPermission.VIEW_METRIC_DEFINITIONS)
    public List<MetricDefinitionResponse> getAll() {
        List<MetricDefinition> metricDefinitions = metricDefinitionService.getAll();
        return metricDefinitions.stream().map(MetricDefinitionResponse::from).toList();
    }
}
