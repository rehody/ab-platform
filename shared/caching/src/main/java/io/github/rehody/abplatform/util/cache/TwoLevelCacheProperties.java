package io.github.rehody.abplatform.util.cache;

import java.time.Duration;

public interface TwoLevelCacheProperties {

    Duration getL1ValueTtl();

    Duration getL1MissTtl();

    long getL1ValueSize();

    long getL1MissSize();

    Duration getL2ValueTtl();

    Duration getL2MissTtl();

    double getTtlSpread();

    String getRedisKeyPrefix();

    String getInvalidationTopic();
}
