package io.github.rehody.abplatform.repository.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.model.audit.AuditDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuditDetailsJsonSerializerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void serializeAndDeserialize_shouldRoundTripDetails() {
        AuditDetailsJsonSerializer serializer = new AuditDetailsJsonSerializer(new ObjectMapper());
        AuditDetails auditDetails = AuditDetails.entry("state", "RUNNING");

        String json = serializer.serialize(auditDetails);
        AuditDetails response = serializer.deserialize(json);

        assertThat(json).contains("RUNNING");
        assertThat(response.values()).containsEntry("state", "RUNNING");
    }

    @Test
    void serialize_shouldWrapSerializationFailure() {
        AuditDetailsJsonSerializer serializer = new AuditDetailsJsonSerializer(objectMapper);
        doThrow(new RuntimeException("boom")).when(objectMapper).writeValueAsString(any());

        assertThatThrownBy(() -> serializer.serialize(AuditDetails.entry("state", "RUNNING")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize audit details");
    }

    @Test
    void deserialize_shouldWrapDeserializationFailure() {
        AuditDetailsJsonSerializer serializer = new AuditDetailsJsonSerializer(objectMapper);
        when(objectMapper.readValue(any(String.class), any(TypeReference.class)))
                .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> serializer.deserialize("{\"state\":\"RUNNING\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to deserialize audit details");
    }
}
