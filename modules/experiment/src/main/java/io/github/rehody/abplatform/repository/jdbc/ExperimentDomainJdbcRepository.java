package io.github.rehody.abplatform.repository.jdbc;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ExperimentDomainJdbcRepository {

    private static final String EXISTS_EXPERIMENT_DOMAIN_BY_KEY_SQL = """
        SELECT EXISTS(
            SELECT 1
            FROM experiment_domains
            WHERE key = :key
        )
        """;

    private final JdbcClient jdbcClient;

    public boolean existsByKey(String key) {
        return jdbcClient
                .sql(EXISTS_EXPERIMENT_DOMAIN_BY_KEY_SQL)
                .param("key", key)
                .query(Boolean.class)
                .single();
    }
}
