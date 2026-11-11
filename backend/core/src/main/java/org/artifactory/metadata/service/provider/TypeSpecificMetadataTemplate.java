package org.artifactory.metadata.service.provider;

import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.artifactory.metadata.service.provider.AbstractMetadataProvider.ARTIFACTORY_LICENSES;

/**
 * Provides type-specific metadata logic
 *
 * @author Uriah Levy
 */
public interface TypeSpecificMetadataTemplate {
    String NAME_SUFFIX = ".name";
    String VERSION_SUFFIX = ".version";
    String DESCRIPTION_SUFFIX = ".description";
    String KEYWORDS_SUFFIX = ".keywords";

    @Nullable
    default String getPkgidSuffix(RepoPath repoPath, Properties properties) {
        return getName(properties, repoPath);
    }

    @Nullable
    String getVersion(RepoPath repoPath, Properties properties);

    default Optional<String> getNameKey() {
        return Optional.empty();
    }

    default Optional<String> getVersionKey() {
        return Optional.empty();
    }

    default Optional<String> getIssuesKey() {
        return Optional.empty();
    }

    default Optional<String> getDescriptionKey() {
        return Optional.empty();
    }

    default Optional<String> getArchitectureKey() {
        return Optional.empty();
    }

    default Optional<String> getDistributionKey() {
        return Optional.empty();
    }

    default String getLicenseKey() {
        return ARTIFACTORY_LICENSES;
    }

    default Map<String, String> getQualifierKeysToMdsKeys() {
        return new HashMap<>();
    }

    default Optional<String> getTagKey() {
        return Optional.empty();
    }

    default Function<String, String> getQualifierValueResolver() {
        return Function.identity();
    }

    default Function<String, Set<String>> getTagValueResolver() {
        return val -> {
            HashSet<String> tags = new HashSet<>();
            tags.add(val);
            return tags;
        };
    }

    /**
     * @return a list of property keys that should be excluded when creating the UserProperties variable.
     * These keys are already set as a unique variable of the model. (i.e. Name, Description, etc.)
     */
    Set<String> getExcludedUserProperties();

    Set<String> getLeadFileExtensions();

    String getName(Properties properties, RepoPath repoPath);

    /**
     * @return true if the template accepts the value of the package description
     */
    default boolean acceptsDescription(@Nonnull String description) {
        return true;
    }
}
