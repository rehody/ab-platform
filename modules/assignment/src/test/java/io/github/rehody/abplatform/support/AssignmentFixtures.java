package io.github.rehody.abplatform.support;

import io.github.rehody.abplatform.enums.ExperimentState;
import io.github.rehody.abplatform.model.Experiment;
import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class AssignmentFixtures {

    private AssignmentFixtures() {}

    public static Experiment runningExperiment(String flagKey, List<ExperimentVariant> variants, long version) {
        return experiment(flagKey, variants, ExperimentState.RUNNING, version);
    }

    public static Experiment experiment(
            String flagKey, List<ExperimentVariant> variants, ExperimentState state, long version) {
        return new Experiment(UUID.randomUUID(), flagKey, List.copyOf(variants), state, version);
    }

    public static ExperimentVariant variant(int position, String key, String value, BigDecimal weight) {
        return new ExperimentVariant(UUID.randomUUID(), key, stringValue(value), position, weight);
    }

    public static ExperimentVariant variant(int position, String key, String value, int weight) {
        return variant(position, key, value, BigDecimal.valueOf(weight));
    }

    public static FeatureValue stringValue(String value) {
        return new FeatureValue(value, FeatureValueType.STRING);
    }

    public static FeatureValue boolValue(boolean value) {
        return new FeatureValue(value, FeatureValueType.BOOL);
    }
}
