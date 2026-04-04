package io.github.rehody.abplatform.risk.repository;

import io.github.rehody.abplatform.risk.enums.ExperimentMetricRiskStatus;
import io.github.rehody.abplatform.risk.model.ExperimentMetricRisk;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ExperimentMetricRiskRepository {

    private static final String SELECT_RISKS_BY_EXPERIMENT_AND_METRIC_SQL = """
        SELECT id,
               experiment_id,
               metric_key,
               variant_id,
               status,
               opened_at,
               resolved_at,
               resolution_comment,
               last_evaluated_at,
               last_bad_deviation,
               worst_bad_deviation,
               auto_paused_at
        FROM experiment_metric_risks
        WHERE experiment_id = :experimentId
          AND metric_key = :metricKey
        ORDER BY variant_id
        """;

    private static final String SELECT_RISK_BY_ID_SQL = """
        SELECT id,
               experiment_id,
               metric_key,
               variant_id,
               status,
               opened_at,
               resolved_at,
               resolution_comment,
               last_evaluated_at,
               last_bad_deviation,
               worst_bad_deviation,
               auto_paused_at
        FROM experiment_metric_risks
        WHERE id = :id
        """;

    private static final String INSERT_RISK_SQL = """
        INSERT INTO experiment_metric_risks (
            id,
            experiment_id,
            metric_key,
            variant_id,
            status,
            opened_at,
            resolved_at,
            resolution_comment,
            last_evaluated_at,
            last_bad_deviation,
            worst_bad_deviation,
            auto_paused_at
        )
        VALUES (
            :id,
            :experimentId,
            :metricKey,
            :variantId,
            :status,
            :openedAt,
            :resolvedAt,
            :resolutionComment,
            :lastEvaluatedAt,
            :lastBadDeviation,
            :worstBadDeviation,
            :autoPausedAt
        )
        """;

    private static final String UPDATE_RISK_SQL = """
        UPDATE experiment_metric_risks
        SET status = :status,
            opened_at = :openedAt,
            resolved_at = :resolvedAt,
            resolution_comment = :resolutionComment,
            last_evaluated_at = :lastEvaluatedAt,
            last_bad_deviation = :lastBadDeviation,
            worst_bad_deviation = :worstBadDeviation,
            auto_paused_at = :autoPausedAt
        WHERE id = :id
        """;

    private final JdbcClient jdbcClient;

    public List<ExperimentMetricRisk> findByExperimentAndMetric(UUID experimentId, String metricKey) {
        return jdbcClient
                .sql(SELECT_RISKS_BY_EXPERIMENT_AND_METRIC_SQL)
                .param("experimentId", experimentId)
                .param("metricKey", metricKey)
                .query(this::mapRow)
                .list();
    }

    public Optional<ExperimentMetricRisk> findById(UUID id) {
        return jdbcClient
                .sql(SELECT_RISK_BY_ID_SQL)
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    public void save(ExperimentMetricRisk risk) {
        jdbcClient
                .sql(INSERT_RISK_SQL)
                .param("id", risk.id())
                .param("experimentId", risk.experimentId())
                .param("metricKey", risk.metricKey())
                .param("variantId", risk.variantId())
                .param("status", risk.status().name())
                .param("openedAt", risk.openedAt())
                .param("resolvedAt", risk.resolvedAt())
                .param("resolutionComment", risk.resolutionComment())
                .param("lastEvaluatedAt", risk.lastEvaluatedAt())
                .param("lastBadDeviation", risk.lastBadDeviation())
                .param("worstBadDeviation", risk.worstBadDeviation())
                .param("autoPausedAt", risk.autoPausedAt())
                .update();
    }

    public void update(ExperimentMetricRisk risk) {
        jdbcClient
                .sql(UPDATE_RISK_SQL)
                .param("id", risk.id())
                .param("status", risk.status().name())
                .param("openedAt", risk.openedAt())
                .param("resolvedAt", risk.resolvedAt())
                .param("resolutionComment", risk.resolutionComment())
                .param("lastEvaluatedAt", risk.lastEvaluatedAt())
                .param("lastBadDeviation", risk.lastBadDeviation())
                .param("worstBadDeviation", risk.worstBadDeviation())
                .param("autoPausedAt", risk.autoPausedAt())
                .update();
    }

    private ExperimentMetricRisk mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ExperimentMetricRisk(
                rs.getObject("id", UUID.class),
                rs.getObject("experiment_id", UUID.class),
                rs.getString("metric_key"),
                rs.getObject("variant_id", UUID.class),
                ExperimentMetricRiskStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("opened_at").toInstant(),
                toInstant(rs, "resolved_at"),
                rs.getString("resolution_comment"),
                rs.getTimestamp("last_evaluated_at").toInstant(),
                rs.getObject("last_bad_deviation", BigDecimal.class),
                rs.getObject("worst_bad_deviation", BigDecimal.class),
                toInstant(rs, "auto_paused_at"));
    }

    private Instant toInstant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        if (timestamp == null) {
            return null;
        }

        return timestamp.toInstant();
    }
}
