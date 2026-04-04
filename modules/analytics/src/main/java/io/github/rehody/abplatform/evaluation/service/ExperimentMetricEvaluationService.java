package io.github.rehody.abplatform.evaluation.service;

import io.github.rehody.abplatform.cache.ExperimentMetricReportCache;
import io.github.rehody.abplatform.cache.ExperimentMetricReportCacheKeyFactory;
import io.github.rehody.abplatform.evaluation.builder.ExperimentMetricEvaluationAssembler;
import io.github.rehody.abplatform.evaluation.model.ExperimentMetricEvaluationReport;
import io.github.rehody.abplatform.evaluation.policy.ExperimentMetricEvaluationPolicy;
import io.github.rehody.abplatform.metric.model.MetricDefinition;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.report.factory.CountableMetricReportAssembler;
import io.github.rehody.abplatform.report.factory.ExperimentReportWindowFactory;
import io.github.rehody.abplatform.report.model.CountableMetricReport;
import io.github.rehody.abplatform.report.model.ExperimentMetricReport;
import io.github.rehody.abplatform.report.model.ExperimentReportWindow;
import io.github.rehody.abplatform.report.repository.AssignmentEventReportRepository;
import io.github.rehody.abplatform.report.repository.CountableMetricEventReportRepository;
import io.github.rehody.abplatform.report.repository.aggregate.AssignmentVariantAggregate;
import io.github.rehody.abplatform.report.repository.aggregate.CountableMetricVariantAggregate;
import io.github.rehody.abplatform.risk.model.ExperimentMetricRisk;
import io.github.rehody.abplatform.risk.service.ExperimentMetricRiskService;
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
public class ExperimentMetricEvaluationService {

    private final ExperimentMetricReportCache experimentMetricReportCache;
    private final ExperimentMetricReportCacheKeyFactory experimentMetricReportCacheKeyFactory;
    private final ExperimentService experimentService;
    private final ExperimentMetricEvaluationPolicy experimentMetricEvaluationPolicy;
    private final AssignmentEventReportRepository assignmentEventReportRepository;
    private final CountableMetricEventReportRepository countableMetricEventReportRepository;
    private final ExperimentReportWindowFactory experimentReportWindowFactory;
    private final CountableMetricReportAssembler countableMetricReportAssembler;
    private final ExperimentMetricEvaluationAssembler experimentMetricEvaluationAssembler;
    private final ExperimentMetricRiskService experimentMetricRiskService;

    @Transactional(readOnly = true)
    public ExperimentMetricEvaluationReport getEvaluationReport(UUID experimentId, String metricKey) {
        Experiment experiment = experimentService.getById(experimentId);
        MetricDefinition metricDefinition =
                experimentMetricEvaluationPolicy.getMetricDefinitionForEvaluation(experiment.id(), metricKey);

        String cacheKey = buildReportCacheKey(experiment.id(), metricKey);

        CountableMetricReport countableMetricReport = experimentMetricReportCache
                .getOrLoad(
                        cacheKey,
                        () -> Optional.of(buildCountableMetricReport(experiment, metricDefinition, Instant.now())))
                .map(this::toCountableMetricReport)
                .orElseThrow(() -> new IllegalStateException("Experiment metric report cache loader returned empty"));

        return buildEvaluationReport(experiment, metricDefinition, countableMetricReport);
    }

    @Transactional
    public void evaluateAndApplyRisk(Experiment experiment, String metricKey) {
        MetricDefinition metricDefinition =
                experimentMetricEvaluationPolicy.getMetricDefinitionForEvaluation(experiment.id(), metricKey);

        ExperimentMetricEvaluationReport report = buildEvaluationReport(experiment, metricDefinition, Instant.now());
        experimentMetricRiskService.applyEvaluation(experiment, metricDefinition, report);
    }

    private ExperimentMetricEvaluationReport buildEvaluationReport(
            Experiment experiment, MetricDefinition metricDefinition, Instant now) {
        return buildEvaluationReport(
                experiment, metricDefinition, buildCountableMetricReport(experiment, metricDefinition, now));
    }

    private ExperimentMetricEvaluationReport buildEvaluationReport(
            Experiment experiment, MetricDefinition metricDefinition, CountableMetricReport countableMetricReport) {

        List<ExperimentVariant> orderedVariants = sortVariantsByPosition(experiment.variants());
        Map<UUID, Integer> participantsByVariant = mapParticipantsByVariantId(countableMetricReport);

        Map<UUID, CountableMetricVariantAggregate> metricAggregatesByVariant =
                mapMetricAggregatesByVariantId(countableMetricReport);

        Map<UUID, ExperimentMetricRisk> risksByVariant =
                mapRisksByVariantId(experimentMetricRiskService.getRisks(experiment.id(), metricDefinition.key()));

        ExperimentReportWindow reportWindow = new ExperimentReportWindow(
                countableMetricReport.meta().trackedFrom(),
                countableMetricReport.meta().trackedTo());

        return experimentMetricEvaluationAssembler.assemble(
                experiment,
                metricDefinition,
                orderedVariants,
                participantsByVariant,
                metricAggregatesByVariant,
                risksByVariant,
                reportWindow);
    }

    private CountableMetricReport buildCountableMetricReport(
            Experiment experiment, MetricDefinition metricDefinition, Instant now) {

        ExperimentReportWindow reportWindow = experimentReportWindowFactory.create(experiment, now);
        List<ExperimentVariant> orderedVariants = sortVariantsByPosition(experiment.variants());

        Map<UUID, Integer> participantsByVariant = mapParticipantsByVariantId(
                assignmentEventReportRepository.findParticipantCountsByVariant(experiment.id(), reportWindow));

        Map<UUID, CountableMetricVariantAggregate> metricAggregatesByVariant =
                mapMetricAggregatesByVariantId(countableMetricEventReportRepository.findMetricStatsByVariant(
                        experiment.id(), metricDefinition.key(), reportWindow));

        return countableMetricReportAssembler.assemble(
                experiment,
                metricDefinition,
                orderedVariants,
                participantsByVariant,
                metricAggregatesByVariant,
                reportWindow);
    }

    private String buildReportCacheKey(UUID experimentId, String metricKey) {
        return experimentMetricReportCacheKeyFactory.forExperimentMetric(experimentId, metricKey);
    }

    private CountableMetricReport toCountableMetricReport(ExperimentMetricReport experimentMetricReport) {
        if (experimentMetricReport instanceof CountableMetricReport countableMetricReport) {
            return countableMetricReport;
        }

        throw new IllegalStateException("Evaluation requires countable metric report");
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

    private Map<UUID, Integer> mapParticipantsByVariantId(CountableMetricReport countableMetricReport) {
        return countableMetricReport.variants().stream()
                .collect(Collectors.toMap(
                        CountableMetricReport.CountableVariantSummary::variantId,
                        CountableMetricReport.CountableVariantSummary::participants));
    }

    private Map<UUID, CountableMetricVariantAggregate> mapMetricAggregatesByVariantId(
            List<CountableMetricVariantAggregate> aggregates) {
        return aggregates.stream()
                .collect(Collectors.toMap(CountableMetricVariantAggregate::variantId, Function.identity()));
    }

    private Map<UUID, CountableMetricVariantAggregate> mapMetricAggregatesByVariantId(
            CountableMetricReport countableMetricReport) {
        return countableMetricReport.variants().stream()
                .collect(Collectors.toMap(
                        CountableMetricReport.CountableVariantSummary::variantId,
                        variant -> new CountableMetricVariantAggregate(
                                variant.variantId(),
                                variant.participantsWithMetricEvent(),
                                variant.totalMetricEvents())));
    }

    private Map<UUID, ExperimentMetricRisk> mapRisksByVariantId(List<ExperimentMetricRisk> risks) {
        return risks.stream().collect(Collectors.toMap(ExperimentMetricRisk::variantId, Function.identity()));
    }
}
