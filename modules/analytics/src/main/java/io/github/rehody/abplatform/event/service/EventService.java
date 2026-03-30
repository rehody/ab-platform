package io.github.rehody.abplatform.event.service;

import io.github.rehody.abplatform.event.dto.request.MetricEventSaveRequest;
import io.github.rehody.abplatform.event.dto.response.MetricEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventService {

    public MetricEventResponse save(MetricEventSaveRequest request) {
        return null;
    }
}
