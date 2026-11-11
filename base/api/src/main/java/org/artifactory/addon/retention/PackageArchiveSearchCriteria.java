package org.artifactory.addon.retention;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.repo.PackageType;

import jakarta.ws.rs.BadRequestException;
import java.util.List;

/**
 * @author Oleksandr Legenkiy
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class PackageArchiveSearchCriteria {
    private static final String WILDCARD = "*";

    private List<String> repositories;
    private String packageType;
    private String name;
    private String version;
    private Long archiveTimeFrom;
    private Long archiveTimeTo;

    public void validate() {
        validateRepos();
        validatePackageType();
        validateArchiveTimes();
    }

    public boolean nameContainsWildcardOnly() {
        return isWildcardOnly(name);
    }

    public boolean nameContainsWildcard() {
        return hasWildcard(name);
    }

    public boolean versionContainsWildcardOnly() {
        return isWildcardOnly(version);
    }

    public boolean versionContainsWildcard() {
        return hasWildcard(version);
    }

    private void validateRepos() {
        if (CollectionUtils.isEmpty(repositories)) {
            return;
        }
        if (repositories.stream().anyMatch(StringUtils::isBlank)) {
            throw new BadRequestException("Repositories cannot contain blank values");
        }
    }

    private void validatePackageType() {
        if (StringUtils.isBlank(packageType)) {
            throw new BadRequestException("Package type cannot be blank");
        }
        try {
            PackageType.valueOf(packageType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(String.format("Invalid packageType value: '%s'", packageType));
        }
    }

    private void validateArchiveTimes() {
        if (archiveTimeFrom != null) {
            if (archiveTimeFrom < 0) {
                throw new BadRequestException("Archive time cannot be negative");
            }
            if (archiveTimeTo != null && archiveTimeTo < archiveTimeFrom) {
                throw new BadRequestException("Param 'archiveTimeTo' should be greater or equal to 'archiveTimeFrom'");
            }
        }
        if (archiveTimeTo != null && archiveTimeTo < 0) {
            throw new BadRequestException("Archive time cannot be negative");
        }
    }

    public static boolean isWildcardOnly(String value) {
        return value != null && value.trim().equals(WILDCARD);
    }

    public static boolean hasWildcard(String value) {
        return value != null && value.trim().contains(WILDCARD);
    }
}
