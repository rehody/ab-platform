package io.github.rehody.abplatform.event.controller;

import io.github.rehody.abplatform.event.dto.request.MetricEventCreateRequest;
import io.github.rehody.abplatform.event.dto.response.MetricEventResponse;
import io.github.rehody.abplatform.event.service.MetricEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class MetricEventController {

    private final MetricEventService metricEventService;

    @PostMapping("/metrics")
    public MetricEventResponse create(@RequestBody MetricEventCreateRequest request) {
        return metricEventService.create(request);
    }
}
