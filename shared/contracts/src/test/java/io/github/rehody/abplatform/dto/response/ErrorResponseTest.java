package io.github.rehody.abplatform.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rehody.abplatform.dto.response.ErrorResponse.ErrorCode;
import io.github.rehody.abplatform.dto.response.ErrorResponse.Violation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorResponseTest {

    @Test
    void constructor_shouldCreateEmptyViolationsAndHandleNullViolations() {
        ErrorResponse response =
                new ErrorResponse(400, ErrorCode.BAD_REQUEST, "bad request", Instant.now(), "/api", null);

        assertThat(response.violations()).isEmpty();
    }

    @Test
    void constructor_shouldCopyViolationsAndKeepReturnedListImmutable() {
        List<Violation> input = new ArrayList<>();
        input.add(new Violation("key", "must not be blank"));

        ErrorResponse response =
                new ErrorResponse(400, ErrorCode.VALIDATION_ERROR, "validation", Instant.now(), "/api", input);

        input.add(new Violation("defaultValue", "type mismatch"));

        assertThat(response.violations()).containsExactly(new Violation("key", "must not be blank"));
        assertThatThrownBy(() -> response.violations().add(new Violation("x", "y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void of_shouldCreateResponseAndPopulateTimestamp() {
        ErrorResponse response = ErrorResponse.of(
                409,
                ErrorCode.CONFLICT,
                "already exists",
                "/api/v1/flags/flag-a",
                List.of(new Violation("key", "dup")));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(response.message()).isEqualTo("already exists");
        assertThat(response.timestamp()).isNotNull();
        assertThat(response.path()).isEqualTo("/api/v1/flags/flag-a");
        assertThat(response.violations()).containsExactly(new Violation("key", "dup"));
    }
}
