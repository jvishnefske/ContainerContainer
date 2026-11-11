package org.artifactory.rest.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorMessagesConstants {
    public static final String BLANK_BUILD_NAME_MESSAGE = "Cannot search without build name.";

    public static final String BLANK_BUILD_NUMBER_AND_STATUS_MESSAGE =
            "Cannot search without build number or build status.";

    public static final String BUILD_NUMBER_AND_STATUS_BOTH_PROVIDED_MESSAGE =
            "Cannot search with both build number and build status parameters, " +
                    "please omit build number if your are looking for latest build by status " +
                    "or omit build status to search for specific build version.";

    public static final String ARCHIVE_TYPE_EMPTY_MESSAGE =
            "Archive type cannot be empty, please provide a type of zip/tar/tar.gz/tgz.";

    public static final String INVALID_MAPPING_OUTPUT_MESSAGE =
            "Invalid output path included.";

    public static final String BUILD_ARTIFACTS_ARCHIVE_NOT_FOUND_MESSAGE =
            "Could not find any build artifacts for build '%s' number '%s'.";

    public static final String BUILD_ARTIFACTS_ARCHIVE_FAILED =
            "Failed to create builds artifacts archive";
}
