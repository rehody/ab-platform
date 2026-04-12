package io.github.rehody.abplatform.model.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditDetailsTest {

    @Test
    void factories_shouldBuildExpectedDetails() {
        AuditDetails entry = AuditDetails.entry("key", "value");
        AuditDetails transition = AuditDetails.transition("state", "DRAFT", "RUNNING");
        AuditDetails stateTransition = AuditDetails.stateTransition("DRAFT", "RUNNING");
        AuditDetails rolloutTransition = AuditDetails.rolloutTransition(5, 15);
        AuditDetails metricBindings =
                AuditDetails.metricBindingsTransition(List.of("orders"), List.of("orders", "revenue"));

        assertThat(entry.values()).containsEntry("key", "value");
        assertThat(transition.values()).containsEntry("state", "DRAFT -> RUNNING");
        assertThat(stateTransition.values()).containsEntry("state", "DRAFT -> RUNNING");
        assertThat(rolloutTransition.values()).containsEntry("rollout", "5 -> 15");
        assertThat(metricBindings.values()).containsEntry("metricBindings", "[orders] -> [orders, revenue]");
    }

    @Test
    void constructor_shouldNormalizeNullAndValidateEntries() {
        AuditDetails empty = new AuditDetails(null);

        assertThat(empty.values()).isEmpty();
        assertThatThrownBy(() -> new AuditDetails(Map.of("", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("key must not be blank");
        assertThatThrownBy(() -> new AuditDetails(Map.of("key", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("value must not be blank");
        LinkedHashMap<String, String> valuesWithNull = new LinkedHashMap<>();
        valuesWithNull.put("key", null);
        assertThatThrownBy(() -> new AuditDetails(valuesWithNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("value must not be blank");
    }

    @Test
    void with_shouldReturnNewValidatedCopy() {
        AuditDetails auditDetails = AuditDetails.entry("state", "DRAFT").with("flagKey", "orders");

        assertThat(auditDetails.values()).containsEntry("state", "DRAFT").containsEntry("flagKey", "orders");
    }

    @Test
    void empty_shouldReturnSharedEmptyShape() {
        assertThat(AuditDetails.empty().values()).isEmpty();
    }
}
