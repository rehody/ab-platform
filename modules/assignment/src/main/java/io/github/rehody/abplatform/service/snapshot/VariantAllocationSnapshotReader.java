package io.github.rehody.abplatform.service.snapshot;

import io.github.rehody.abplatform.model.Experiment;

public interface VariantAllocationSnapshotReader {

    VariantAllocationSnapshot get(Experiment experiment);
}
