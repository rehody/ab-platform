package io.github.rehody.abplatform.report.model;

import java.time.Instant;

public record ExperimentReportWindow(Instant trackedFrom, Instant trackedTo) {}
