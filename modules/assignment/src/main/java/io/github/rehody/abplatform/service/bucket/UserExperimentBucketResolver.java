package io.github.rehody.abplatform.service.bucket;

import static io.github.rehody.abplatform.service.VariantBucketPolicy.BUCKET_POOL_SIZE;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UserExperimentBucketResolver {

    private static final HashFunction VARIANT_HASH_FUNCTION = Hashing.murmur3_32_fixed();

    public int resolve(UUID experimentId, UUID userId) {
        Objects.requireNonNull(experimentId, "experimentId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");

        String hashInput = userId + ":" + experimentId;
        int hash = VARIANT_HASH_FUNCTION
                .hashString(hashInput, StandardCharsets.UTF_8)
                .asInt();

        return Integer.remainderUnsigned(hash, BUCKET_POOL_SIZE);
    }
}
