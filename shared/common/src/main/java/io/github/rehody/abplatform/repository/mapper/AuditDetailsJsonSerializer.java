package io.github.rehody.abplatform.repository.mapper;

import io.github.rehody.abplatform.model.audit.AuditDetails;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class AuditDetailsJsonSerializer {

    private static final TypeReference<Map<String, String>> DETAILS_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public String serialize(AuditDetails auditDetails) {
        try {
            return objectMapper.writeValueAsString(auditDetails.values());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize audit details", ex);
        }
    }

    public AuditDetails deserialize(String detailsJson) {
        try {
            return new AuditDetails(objectMapper.readValue(detailsJson, DETAILS_TYPE));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize audit details", ex);
        }
    }
}
