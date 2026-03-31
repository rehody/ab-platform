package io.github.rehody.abplatform.report.repository.aggregate;

import java.util.UUID;

public record UniqueMetricVariantAggregate(UUID variantId, int participantsWithMetricEvent) {}
