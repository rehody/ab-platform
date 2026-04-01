package io.github.rehody.abplatform.repository.sync;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.model.FeatureValue;
import io.github.rehody.abplatform.model.FeatureValue.FeatureValueType;
import io.github.rehody.abplatform.repository.jdbc.ExperimentVariantJdbcRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentVariantSynchronizerTest {

    @Mock
    private ExperimentVariantJdbcRepository experimentVariantJdbcRepository;

    @Test
    void sync_shouldSplitIncomingVariantsIntoDeleteUpdateAndInsertBatches() {
        UUID experimentId = UUID.randomUUID();
        UUID toUpdateId = UUID.randomUUID();
        UUID toDeleteId = UUID.randomUUID();
        UUID toInsertId = UUID.randomUUID();
        ExperimentVariant toUpdate = new ExperimentVariant(
                toUpdateId, "control", new FeatureValue(true, FeatureValueType.BOOL), 0, BigDecimal.ONE);
        ExperimentVariant toInsert = new ExperimentVariant(
                toInsertId, "variant-a", new FeatureValue("blue", FeatureValueType.STRING), 1, BigDecimal.ONE);
        ExperimentVariantSynchronizer synchronizer = new ExperimentVariantSynchronizer(experimentVariantJdbcRepository);

        when(experimentVariantJdbcRepository.findIdsByExperimentId(experimentId))
                .thenReturn(List.of(toUpdateId, toDeleteId));

        synchronizer.sync(experimentId, List.of(toUpdate, toInsert));

        verify(experimentVariantJdbcRepository).batchDelete(experimentId, List.of(toDeleteId));
        verify(experimentVariantJdbcRepository).batchUpdate(experimentId, List.of(toUpdate));
        verify(experimentVariantJdbcRepository).batchInsert(experimentId, List.of(toInsert));
    }
}
