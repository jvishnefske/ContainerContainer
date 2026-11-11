package org.artifactory.application.model;

import jakarta.annotation.Nonnull;

import java.util.Map;

public record ApplicationServiceSBOM(@Nonnull String applicationKey, @Nonnull String packageType,
                                     @Nonnull String packageName, String packageVersion,
                                     @Nonnull Map<String, String> props) {
    public String getPackageDetails() {
        return String.join("_", packageType, packageName, packageVersion);
    }
}
