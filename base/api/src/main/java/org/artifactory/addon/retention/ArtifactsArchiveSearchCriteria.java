package org.artifactory.addon.retention;

import lombok.Builder;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import jakarta.ws.rs.BadRequestException;
import java.util.List;

/*
 * @author punyap
 */
@Builder(toBuilder = true)
public record ArtifactsArchiveSearchCriteria(
        List<String> repositories,
        String artifactPath,
        String artifactName,
        Long archiveTimeFrom,
        Long archiveTimeTo,
        List<Property> properties,
        String sha256,
        String actualSha1,
        String actualMd5) {
    private static final String WILDCARD = "*";
    public void validate() {
        validateRepos();
        validateArchiveTimes();
        validateArtifactPath();
        validateProperties();
    }
    private void validateProperties() {
        if (CollectionUtils.isEmpty(properties)) {
            return;
        }
        properties.forEach(ArtifactsArchiveSearchCriteria.Property::validateProperty);
    }

    private void validateArtifactPath() {
        if (artifactPath == null) {
            return;
        }
        if (StringUtils.isBlank(artifactPath)) {
            throw new BadRequestException("Artifact path cannot be blank");
        }
    }

    public boolean nameContainsWildcardOnly() {
        return isWildcardOnly(artifactName);
    }

    public boolean nameContainsWildcard() {
        return hasWildcard(artifactName);
    }

    public boolean artifactPathContainsWildcard() {
        return hasWildcard(artifactPath);
    }

    public boolean artifactPathContainsWildcardOnly() {
        return isWildcardOnly(artifactPath);
    }

    private void validateRepos() {
        if (CollectionUtils.isEmpty(repositories)) {
            return;
        }
        if (repositories.stream().anyMatch(StringUtils::isBlank)) {
            throw new BadRequestException("Repositories cannot contain blank values");
        }
    }

    private void validateArchiveTimes() {
        if (archiveTimeFrom == null && archiveTimeTo == null) {
            return;
        }
        if ((archiveTimeTo != null && archiveTimeTo < 0) || (archiveTimeFrom != null && archiveTimeFrom < 0)) {
            throw new BadRequestException("Archive time cannot be negative");
        }
        if (archiveTimeTo != null && archiveTimeFrom != null && archiveTimeTo < archiveTimeFrom) {
            throw new BadRequestException("Param 'archiveTimeTo' should be greater or equal to 'archiveTimeFrom'");
        }
    }

    public static boolean isWildcardOnly(String value) {
        return value != null && value.trim().equals(WILDCARD);
    }

    public static boolean hasWildcard(String value) {
        return value != null && value.trim().contains(WILDCARD);
    }

    public record Property(String key, String value) {

        void validateProperty() {
            if (StringUtils.isBlank(key) || StringUtils.isBlank(value)) {
                throw new BadRequestException("Property key and value cannot be blank");
            }
            if (isWildcardOnly(value)) {
                throw new BadRequestException("Property value cannot be a wildcard only");
            }
        }
        public boolean propertyValueContainsWildcard() {
            return hasWildcard(value);
        }
    }
}
