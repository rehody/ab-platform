package io.github.rehody.abplatform.report.service;

import io.github.rehody.abplatform.cache.ExperimentMetricReportCache;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.service.MetricDefinitionService;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.report.factory.CountableMetricReportAssembler;
import io.github.rehody.abplatform.report.factory.ExperimentReportWindowFactory;
import io.github.rehody.abplatform.report.factory.UniqueMetricReportAssembler;
import io.github.rehody.abplatform.report.model.ExperimentMetricReport;
import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import io.github.rehody.abplatform.report.repository.AssignmentEventReportRepository;
import io.github.rehody.abplatform.report.repository.CountableMetricEventReportRepository;
import io.github.rehody.abplatform.report.repository.UniqueMetricEventReportRepository;
import io.github.rehody.abplatform.report.repository.aggregate.AssignmentVariantAggregate;
import io.github.rehody.abplatform.report.repository.aggregate.CountableMetricVariantAggregate;
import io.github.rehody.abplatform.report.repository.aggregate.UniqueMetricVariantAggregate;
import io.github.rehody.abplatform.service.ExperimentService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExperimentReportService {

    private final ExperimentMetricReportCache experimentMetricReportCache;
    private final ExperimentService experimentService;
    private final MetricDefinitionService metricDefinitionService;
    private final AssignmentEventReportRepository assignmentEventReportRepository;
    private final UniqueMetricEventReportRepository uniqueMetricEventReportRepository;
    private final CountableMetricEventReportRepository countableMetricEventReportRepository;
    private final ExperimentReportWindowFactory experimentReportWindowFactory;
    private final UniqueMetricReportAssembler uniqueMetricReportAssembler;
    private final CountableMetricReportAssembler countableMetricReportAssembler;

    @Transactional(readOnly = true)
    public ExperimentMetricReport getExperimentReport(UUID experimentId, String metricKey) {
        return experimentMetricReportCache
                .getOrLoad(cacheKey(experimentId, metricKey), () -> Optional.of(loadReport(experimentId, metricKey)))
                .orElseThrow(() -> new IllegalStateException("Experiment metric report cache loader returned empty"));
    }

    private ExperimentMetricReport loadReport(UUID experimentId, String metricKey) {
        Instant now = Instant.now();

        Experiment experiment = experimentService.getById(experimentId);
        ExperimentReportWindow reportWindow = experimentReportWindowFactory.create(experiment, now);
        MetricDefinition metricDefinition = metricDefinitionService.getByKey(metricKey);

        List<ExperimentVariant> orderedVariants = sortVariantsByPosition(experiment.variants());

        Map<UUID, Integer> participantsByVariant = mapParticipantsByVariantId(
                assignmentEventReportRepository.findParticipantCountsByVariant(experimentId, reportWindow));

        return switch (metricDefinition.type()) {
            case UNIQUE ->
                uniqueMetricReportAssembler.assemble(
                        experiment,
                        metricDefinition,
                        orderedVariants,
                        participantsByVariant,
                        mapUniqueMetricAggregatesByVariantId(
                                uniqueMetricEventReportRepository.findParticipantCountsByVariant(
                                        experiment.id(), metricDefinition.key(), reportWindow)),
                        reportWindow);

            case COUNTABLE ->
                countableMetricReportAssembler.assemble(
                        experiment,
                        metricDefinition,
                        orderedVariants,
                        participantsByVariant,
                        mapCountableMetricAggregatesByVariantId(
                                countableMetricEventReportRepository.findMetricStatsByVariant(
                                        experiment.id(), metricDefinition.key(), reportWindow)),
                        reportWindow);
        };
    }

    private String cacheKey(UUID experimentId, String metricKey) {
        return "%s:metric:%s".formatted(experimentId, metricKey);
    }

    private List<ExperimentVariant> sortVariantsByPosition(List<ExperimentVariant> variants) {
        return variants.stream()
                .sorted(Comparator.comparingInt(ExperimentVariant::position))
                .toList();
    }

    private Map<UUID, Integer> mapParticipantsByVariantId(List<AssignmentVariantAggregate> aggregates) {
        return aggregates.stream()
                .collect(Collectors.toMap(
                        AssignmentVariantAggregate::variantId, AssignmentVariantAggregate::participants));
    }

    private Map<UUID, UniqueMetricVariantAggregate> mapUniqueMetricAggregatesByVariantId(
            List<UniqueMetricVariantAggregate> aggregates) {
        return aggregates.stream()
                .collect(Collectors.toMap(UniqueMetricVariantAggregate::variantId, Function.identity()));
    }

    private Map<UUID, CountableMetricVariantAggregate> mapCountableMetricAggregatesByVariantId(
            List<CountableMetricVariantAggregate> aggregates) {
        return aggregates.stream()
                .collect(Collectors.toMap(CountableMetricVariantAggregate::variantId, Function.identity()));
    }
}
