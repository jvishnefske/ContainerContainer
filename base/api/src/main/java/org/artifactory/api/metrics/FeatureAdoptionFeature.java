package org.artifactory.api.metrics;

/**
 * Holds a list of all features that are tracked as part of Feature Adoption.
 *
 * @author Oleksandr Legenkiy
 */
public enum FeatureAdoptionFeature {

    COLD_STORAGE("cold_storage"),
    PUB_REPOSITORY("pub_repository"),
    NUGET_REPOSITORY("nuget_repository"),
    FEDERATED_REPO("federated_repo"),
    SWIFT_REPOSITORY("swift_repository"),
    TERRAFORM_BACKEND_REPOSITORY("terraformbackend_repository"),
    TERRAFORM_REPOSITORY("terraform_repository"),
    OCI_IMAGE("oci_image");

    private final String name;

    FeatureAdoptionFeature(String name) {
        this.name = name;
    }

    public String featureName() {
        return name;
    }
}
