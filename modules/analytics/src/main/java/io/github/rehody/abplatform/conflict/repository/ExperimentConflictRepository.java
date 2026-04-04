package io.github.rehody.abplatform.conflict.repository;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.repository.rowmapper.ExperimentRowMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ExperimentConflictRepository {

    private static final String SELECT_POTENTIAL_CONFLICTS_SQL = """
        SELECT id, flag_key, domain_key, state, version, started_at, completed_at
        FROM experiments
        WHERE id <> :experimentId
          AND state NOT IN (:completedState, :archivedState)
          AND (flag_key = :flagKey OR domain_key = :domainKey)
        ORDER BY CASE
            WHEN state = :runningState THEN 0
            ELSE 1 END,
          created_at DESC,
          id
        """;

    private final JdbcClient jdbcClient;
    private final ExperimentRowMapper experimentRowMapper;

    public List<Experiment> findAll(UUID experimentId, String flagKey, String domainKey) {
        return jdbcClient
                .sql(SELECT_POTENTIAL_CONFLICTS_SQL)
                .param("experimentId", experimentId)
                .param("flagKey", flagKey)
                .param("domainKey", domainKey)
                .param("runningState", ExperimentState.RUNNING.toString())
                .param("completedState", ExperimentState.COMPLETED.toString())
                .param("archivedState", ExperimentState.ARCHIVED.toString())
                .query(experimentRowMapper)
                .list();
    }
}
