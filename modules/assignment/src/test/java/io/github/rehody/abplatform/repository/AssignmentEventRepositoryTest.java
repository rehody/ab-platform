package io.github.rehody.abplatform.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.model.AssignmentEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class AssignmentEventRepositoryTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    private AssignmentEventRepository assignmentEventRepository;

    @BeforeEach
    void setUp() {
        assignmentEventRepository = new AssignmentEventRepository(jdbcClient);
    }

    @Test
    void saveIfAbsent_shouldWriteAllParametersAndExecuteInsert() {
        AssignmentEvent assignmentEvent = new AssignmentEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-04-06T10:15:30Z"));
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(1);

        assignmentEventRepository.saveIfAbsent(assignmentEvent);

        verify(jdbcClient).sql(contains("INSERT INTO assignment_events"));
        verify(statementSpec).param("id", assignmentEvent.id());
        verify(statementSpec).param("userId", assignmentEvent.userId());
        verify(statementSpec).param("variantId", assignmentEvent.variantId());
        verify(statementSpec).param("experimentId", assignmentEvent.experimentId());
        verify(statementSpec).param("timestamp", assignmentEvent.timestamp());
        verify(statementSpec).update();
    }
}
