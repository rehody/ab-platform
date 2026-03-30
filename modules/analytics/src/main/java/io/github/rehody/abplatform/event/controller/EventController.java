package io.github.rehody.abplatform.event.controller;

import io.github.rehody.abplatform.event.dto.request.MetricEventSaveRequest;
import io.github.rehody.abplatform.event.dto.response.MetricEventResponse;
import io.github.rehody.abplatform.event.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping("/metrics")
    public MetricEventResponse save(@RequestBody MetricEventSaveRequest request) {
        return eventService.save(request);
    }
}
