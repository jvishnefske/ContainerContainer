package com.jfrog.build.gateway;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.evidence.EvidenceCreationService;
import org.jfrog.build.api.release.PromotionStatus;

import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;

@Slf4j
@RequiredArgsConstructor
public class BuildEvidenceGateway {

    public void createEvidenceForBuildPromotion(
            @NonNull String buildRepo,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String started,
            @NonNull PromotionStatus promotionStatus) {
        getEvidenceCreationService().ifPresent(service -> service.createAndDeployEvidenceForBuildPromotion(
                buildRepo,
                buildName,
                buildNumber,
                started,
                promotionStatus
        ));
    }

    /**
     * Available only for Artifactory PRO.
     */
    @NonNull
    private Optional<EvidenceCreationService> getEvidenceCreationService() {
        try {
            return of(ContextHelper.get().beanForType(EvidenceCreationService.class));
        } catch (RuntimeException e) {
            log.warn("Failed to get EvidenceCreationService: {}", e.getMessage());
            return empty();
        }
    }
}
