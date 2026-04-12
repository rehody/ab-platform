package io.github.rehody.abplatform.repository.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class ExperimentDomainJdbcRepositoryTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    @Mock
    private JdbcClient.MappedQuerySpec<Boolean> booleanQuerySpec;

    private ExperimentDomainJdbcRepository experimentDomainJdbcRepository;

    @BeforeEach
    void setUp() {
        experimentDomainJdbcRepository = new ExperimentDomainJdbcRepository(jdbcClient);
    }

    @Test
    void existsByKey_shouldReturnBooleanFromJdbc() {
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("key", "CHECKOUT")).thenReturn(statementSpec);
        when(statementSpec.query(Boolean.class)).thenReturn(booleanQuerySpec);
        when(booleanQuerySpec.single()).thenReturn(Boolean.TRUE);

        boolean exists = experimentDomainJdbcRepository.existsByKey("CHECKOUT");

        assertThat(exists).isTrue();
        verify(jdbcClient).sql(contains("SELECT EXISTS"));
    }
}
