/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo;

import jakarta.annotation.Nullable;
import org.artifactory.common.config.metadata.MetadataEnum;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An object that defines the type for the repository provisioning
 *
 * @author Noam Y. Tenne
 */
public enum RepoDetailsType implements MetadataEnum {
    LOCAL("Local"), REMOTE("Remote"), VIRTUAL("Virtual"), FEDERATED("Federated"),
    // legacy RBv1
    RELEASE_BUNDLE("Release Bundle");

    private final String typeName;
    private final String typeNameLowercase;

    //Until someone finds me a way to reference an enum string value in a switch statement...
    //(Yes i'm looking at you jls 15.28 https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.28)
    public static final String LOCAL_REPO = "local";
    public static final String CACHED_REPO = "cached";
    public static final String REMOTE_REPO = "remote";
    public static final String VIRTUAL_REPO = "virtual";
    public static final String DISTRIBUTION_REPO = "distribution";
    public static final String RELEASE_BUNDLE_REPO = "releaseBundles";
    public static final String SUPPORT_BUNDLES_REPO = "supportBundles";
    public static final String FEDERATED_REPO = "federated";
    public static final String FEDERATED_BUILD_REPO = "federatedBuild";

    /**
     * Main constructor
     *
     * @param typeName The display name of the type
     */
    RepoDetailsType(String typeName) {
        this.typeName = typeName;
        this.typeNameLowercase = typeName.toLowerCase();
    }

    public static RepoDetailsType byNativeName(String nativeName) {
        if (nativeName == null) {
            return null;
        }
        for (RepoDetailsType repoType : values()) {
            if (repoType.typeNameLowercase.equals(nativeName.trim())) {
                return repoType;
            }
        }
        return null;
    }

    /**
     * Matches a string to a corresponding RepoDetailsType value. Case-insensitive.<br/>
     * If input string equals (ignoring the case) to "distribution" or to "ReleaseBundle", then it's mapped to
     * {@link RepoDetailsType#RELEASE_BUNDLE} value
     *
     * @return a matching enum value. <code>null</code> if no matching value found.
     */
    @Nullable
    public static RepoDetailsType fromString(String input) {
        if (input == null) {
            return null;
        }

        String normalizedInput = input.trim().replace(" ", "");
        for (RepoDetailsType repoType : values()) {
            if (repoType.typeNameLowercase.replace(" ", "").equalsIgnoreCase(normalizedInput)) {
                return repoType;
            }
        }
        if (DISTRIBUTION_REPO.equalsIgnoreCase(normalizedInput) ||
                RELEASE_BUNDLE_REPO.equalsIgnoreCase(normalizedInput)) {
            return RELEASE_BUNDLE;
        }
        return null;
    }

    public static Set<RepoDetailsType> getRepoDetailsType(Set<String> types) {
        if (types == null || types.isEmpty()) {
            return new HashSet<>(Arrays.asList(RepoDetailsType.values()));
        }
        return types.stream()
                .map(type -> {
                    for (RepoDetailsType repoType : values()) {
                        if (repoType.typeNameLowercase.replace(" ", "").equals(type.toLowerCase())) {
                            return repoType;
                        }
                    }
                    throw new IllegalArgumentException("Invalid repository type: " + type);
                })
                .collect(Collectors.toSet());
    }

    /**
     * Returns the display name of the type
     *
     * @return Type display name
     */
    public String typeName() {
        return typeName;
    }

    public String typeNameLowercase() {
        return typeNameLowercase;
    }

    @Override
    public String getConfigName() {
        return typeNameLowercase;
    }
}
