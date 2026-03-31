package io.github.rehody.abplatform.report.service;

import io.github.rehody.abplatform.exception.ExperimentNotFoundException;
import io.github.rehody.abplatform.exception.MetricDefinitionNotFoundException;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.metric.repository.MetricDefinitionRepository;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.report.dto.response.ExperimentMetricReportResponse;
import io.github.rehody.abplatform.report.factory.CountableMetricReportAssembler;
import io.github.rehody.abplatform.report.factory.ExperimentReportWindowFactory;
import io.github.rehody.abplatform.report.factory.UniqueMetricReportAssembler;
import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import io.github.rehody.abplatform.report.repository.AssignmentEventReportRepository;
import io.github.rehody.abplatform.report.repository.CountableMetricEventReportRepository;
import io.github.rehody.abplatform.report.repository.UniqueMetricEventReportRepository;
import io.github.rehody.abplatform.report.repository.aggregate.AssignmentVariantAggregate;
import io.github.rehody.abplatform.report.repository.aggregate.CountableMetricVariantAggregate;
import io.github.rehody.abplatform.report.repository.aggregate.UniqueMetricVariantAggregate;
import io.github.rehody.abplatform.repository.ExperimentRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExperimentReportService {

    private final ExperimentRepository experimentRepository;
    private final MetricDefinitionRepository metricDefinitionRepository;
    private final AssignmentEventReportRepository assignmentEventReportRepository;
    private final UniqueMetricEventReportRepository uniqueMetricEventReportRepository;
    private final CountableMetricEventReportRepository countableMetricEventReportRepository;
    private final ExperimentReportWindowFactory experimentReportWindowFactory;
    private final UniqueMetricReportAssembler uniqueReportAssembler;
    private final CountableMetricReportAssembler countableReportAssembler;

    public ExperimentMetricReportResponse getExperimentReport(UUID experimentId, String metricKey) {
        Instant now = Instant.now();

        Experiment experiment = findExperimentOrThrow(experimentId);
        ExperimentReportWindow reportWindow = experimentReportWindowFactory.create(experiment, now);
        MetricDefinition metricDefinition = findMetricDefinitionOrThrow(metricKey);

        List<ExperimentVariant> orderedVariants = sortVariantsByPosition(experiment.variants());

        Map<UUID, Integer> participantsByVariant = mapParticipantsByVariantId(
                assignmentEventReportRepository.findParticipantCountsByVariant(experimentId, reportWindow));

        return switch (metricDefinition.type()) {
            case UNIQUE ->
                uniqueReportAssembler.assemble(
                        experiment,
                        metricDefinition,
                        orderedVariants,
                        participantsByVariant,
                        mapUniqueMetricAggregatesByVariantId(
                                uniqueMetricEventReportRepository.findParticipantCountsByVariant(
                                        experiment.id(), metricDefinition.key(), reportWindow)),
                        reportWindow);

            case COUNTABLE ->
                countableReportAssembler.assemble(
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

    private Experiment findExperimentOrThrow(UUID experimentId) {
        return experimentRepository
                .findById(experimentId)
                .orElseThrow(
                        () -> new ExperimentNotFoundException("Experiment '%s' not found".formatted(experimentId)));
    }

    private MetricDefinition findMetricDefinitionOrThrow(String metricKey) {
        return metricDefinitionRepository
                .findByKey(metricKey)
                .orElseThrow(() ->
                        new MetricDefinitionNotFoundException("Metric definition '%s' not found".formatted(metricKey)));
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
