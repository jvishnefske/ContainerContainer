package org.artifactory.metrics.framework.adoption.packages;

import org.artifactory.api.metrics.FeatureAdoptionFeature;

/**
 * @author Shahaf Golan
 */
public enum FeatureAdoptionPackageRepository {

    PUB_REPOSITORY("pub", FeatureAdoptionFeature.PUB_REPOSITORY),
    SWIFT_REPOSITORY("swift", FeatureAdoptionFeature.SWIFT_REPOSITORY),
    TERRAFORM_BACKEND_REPOSITORY("terraformbackend", FeatureAdoptionFeature.TERRAFORM_BACKEND_REPOSITORY),
    TERRAFORM_REPOSITORY("terraform", FeatureAdoptionFeature.TERRAFORM_REPOSITORY);

    private final String type;
    private final FeatureAdoptionFeature feature;

    FeatureAdoptionPackageRepository(String type, FeatureAdoptionFeature feature) {
        this.type = type;
        this.feature = feature;
    }

    public FeatureAdoptionFeature feature() {
        return feature;
    }

    public static FeatureAdoptionPackageRepository fromType(String type) {
        for (FeatureAdoptionPackageRepository featureAdoptionPackageRepo : values()) {
            if (type.equalsIgnoreCase(featureAdoptionPackageRepo.type)) {
                return featureAdoptionPackageRepo;
            }
        }
        return null;
    }
}
