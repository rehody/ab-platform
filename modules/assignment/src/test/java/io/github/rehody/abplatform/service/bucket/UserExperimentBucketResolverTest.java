package io.github.rehody.abplatform.service.bucket;

import static io.github.rehody.abplatform.service.VariantBucketPolicy.BUCKET_POOL_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserExperimentBucketResolverTest {

    private final UserExperimentBucketResolver userExperimentBucketResolver = new UserExperimentBucketResolver();

    @Test
    void resolve_shouldBeDeterministicAndStayWithinBucketPoolRange() {
        UUID experimentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        int first = userExperimentBucketResolver.resolve(experimentId, userId);
        int second = userExperimentBucketResolver.resolve(experimentId, userId);

        assertThat(first).isEqualTo(second);
        assertThat(first).isBetween(0, BUCKET_POOL_SIZE - 1);
    }

    @Test
    void resolve_shouldRejectNullExperimentId() {
        assertThatThrownBy(() -> userExperimentBucketResolver.resolve(null, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("experimentId must not be null");
    }

    @Test
    void resolve_shouldRejectNullUserId() {
        assertThatThrownBy(() -> userExperimentBucketResolver.resolve(UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userId must not be null");
    }
}
