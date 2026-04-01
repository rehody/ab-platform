package io.github.rehody.abplatform.policy;

public interface FeatureFlagUpdatePolicy {

    boolean canUpdateDefaultValue(String flagKey);
}
