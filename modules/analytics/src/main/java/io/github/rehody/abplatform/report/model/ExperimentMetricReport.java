package io.github.rehody.abplatform.report.model;

public sealed interface ExperimentMetricReport permits CountableMetricReport, UniqueMetricReport {}
