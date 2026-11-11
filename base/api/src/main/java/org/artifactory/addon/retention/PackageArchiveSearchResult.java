package org.artifactory.addon.retention;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.retention.exception.PackageArchiveSearchException;
import org.artifactory.aql.result.Row;

import java.util.List;

/**
 * @author Oleksandr Legenkiy
 */
@Data
@Slf4j
public class PackageArchiveSearchResult {

    private static final String PACKAGE_TYPE_PROP_KEY = "archive.packageType";
    private static final String PACKAGE_NAME_PROP_KEY = "archive.packageName";
    private static final String VERSION_PROP_KEY = "archive.version";
    private static final String ORIG_REPO_PROP_KEY = "archive.repoName";
    private static final String ARCHIVE_TIME_PROP_KEY = "archive.time";

    private String manifestName;
    private String packageType;
    private String packageName;
    private String version;
    private String originalRepository;
    private Long archivingTime;

    public PackageArchiveSearchResult(Row row) {
        if (row != null) {
            this.manifestName = row.itemName;
            initFromProperties(row.properties);
        }
        validateAllValuesPresentAfterInit();
    }

    private void initArchivingTime(String time) {
        if (StringUtils.isNotBlank(time)) {
            try {
                this.archivingTime = Long.valueOf(time);
            } catch (NumberFormatException e) {
                throw new PackageArchiveSearchException("Cannot parse archive date property value: " + time);
            }
        }
    }

    private void initFromProperties(List<Row> properties) {
        if (properties != null) {
            properties.forEach(propRow -> {
                if (StringUtils.isNotBlank(propRow.propertyKey)) {
                    switch (propRow.propertyKey) {
                        case PACKAGE_TYPE_PROP_KEY -> this.packageType = propRow.propertyValue;
                        case PACKAGE_NAME_PROP_KEY -> this.packageName = propRow.propertyValue;
                        case VERSION_PROP_KEY -> this.version = propRow.propertyValue;
                        case ORIG_REPO_PROP_KEY -> this.originalRepository = propRow.propertyValue;
                        case ARCHIVE_TIME_PROP_KEY -> initArchivingTime(propRow.propertyValue);
                        default -> log.trace("Skip manifest property key found when parsing AQL response: " +
                                propRow.propertyKey);
                    }
                }
            });
        }
    }

    private void validateAllValuesPresentAfterInit() {
        validateValueIsPresent(manifestName, "manifestName");
        validateValueIsPresent(packageType, "packageType");
        validateValueIsPresent(packageName, "packageName");
        validateValueIsPresent(version, "version");
        validateValueIsPresent(originalRepository, "originalRepository");
        validateValueIsPresent(archivingTime != null ? String.valueOf(archivingTime) : null, "archivingTime");
    }

    private void validateValueIsPresent(String value, String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new PackageArchiveSearchException("Missing required package archive data for field: " + fieldName);
        }
    }
}
