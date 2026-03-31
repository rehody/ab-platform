package io.github.rehody.abplatform.report.repository.aggregate;

import java.util.UUID;

public record CountableMetricVariantAggregate(UUID variantId, int participantsWithMetricEvent, int totalMetricEvents) {}
