package io.github.rehody.abplatform.repository.sync;

import io.github.rehody.abplatform.model.ExperimentVariant;
import io.github.rehody.abplatform.repository.jdbc.ExperimentVariantJdbcRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperimentVariantSynchronizer {

    private final ExperimentVariantJdbcRepository experimentVariantJdbcRepository;

    public void sync(UUID experimentId, List<ExperimentVariant> variants) {
        Set<UUID> existingIds = new HashSet<>(experimentVariantJdbcRepository.findIdsByExperimentId(experimentId));
        Set<UUID> incomingIds = variants.stream().map(ExperimentVariant::id).collect(Collectors.toSet());

        List<UUID> variantIdsToDelete = existingIds.stream()
                .filter(existingId -> !incomingIds.contains(existingId))
                .toList();

        List<ExperimentVariant> variantsToUpdate = variants.stream()
                .filter(variant -> existingIds.contains(variant.id()))
                .toList();

        List<ExperimentVariant> variantsToInsert = variants.stream()
                .filter(variant -> !existingIds.contains(variant.id()))
                .toList();

        experimentVariantJdbcRepository.batchDelete(experimentId, variantIdsToDelete);
        experimentVariantJdbcRepository.batchUpdate(experimentId, variantsToUpdate);
        experimentVariantJdbcRepository.batchInsert(experimentId, variantsToInsert);
    }
}
