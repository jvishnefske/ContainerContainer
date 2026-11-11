package org.artifactory.addon.huggingfaceml;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

/**
 * @author Volodymyr Shulga
 */
@Slf4j
@UtilityClass
public class HuggingFacePathUtil {
    public static final String MODEL_LEAD_FILE_NAME = ".jfrog_huggingface_model_info.json";
    public static final String DATASET_LEAD_FILE_NAME = ".jfrog_huggingface_dataset_info.json";
    public static final String LATEST_MODEL_LEAD_FILE_NAME = ".latest_huggingface_model_info.json";
    public static final String LATEST_DATASET_LEAD_FILE_NAME = ".latest_huggingface_dataset_info.json";
    public static final String ML_LAYOUT_LATEST_LEAD_FILES_FOLDER = ".jfrog/ml_layout/latest_lead_files";
    private static final int POSITION_OF_REVISION_FROM_END = 3;
    private static final int POSITION_OF_TIMESTAMP_FROM_END = 2;

    public static final String DATE_TIME_PATTERN =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$";

    public boolean isValidPath(String path) {
        EntityType entityType = getEntityType(path);
        FileType fileType = getFileType(path);
        if (!isPathMatchingOldLayout(path, entityType, fileType)) {
            log.warn("Path {} doesn't match the HF old layout", path);
            return false;
        }

        return true;
    }

    public String convertPathToNewLayout(@Nonnull String path) {
        EntityType entityType = getEntityType(path);
        FileType fileType = getFileType(path);
        if (!isValidPath(path)) {
            return null;
        }
        return parsePath(path, entityType, fileType);
    }

    public boolean isLeadFile(@Nonnull String path) {
        return getFileType(path) == FileType.LEAD_FILE;
    }

    @Nonnull
    public String getRevisionWithTimestampForLeadFile(@Nonnull String[] tokens) {
        if (tokens.length < POSITION_OF_REVISION_FROM_END) {
            log.warn("Invalid tokens length for path: '{}'", String.join("/", tokens));
            return "";
        }
        return tokens[tokens.length - POSITION_OF_REVISION_FROM_END] +
                "_" + tokens[tokens.length - POSITION_OF_TIMESTAMP_FROM_END];
    }

    @VisibleForTesting
    EntityType getEntityType(@Nonnull String path) {
        if (path.startsWith("models")) {
            return EntityType.MODEL;
        } else if (path.startsWith("datasets")) {
            return EntityType.DATASET;
        } else {
            return EntityType.UNKNOWN;
        }
    }

    public static FileType getFileType(@Nonnull String path) {
        if (path.endsWith(MODEL_LEAD_FILE_NAME) || path.endsWith(DATASET_LEAD_FILE_NAME)) {
            return FileType.LEAD_FILE;
        } else if (path.endsWith(LATEST_MODEL_LEAD_FILE_NAME) || path.endsWith(LATEST_DATASET_LEAD_FILE_NAME)) {
            return FileType.LATEST_LEAD_FILE;
        } else {
            return FileType.GENERIC;
        }
    }

    @VisibleForTesting
    boolean isPathMatchingOldLayout(String path, EntityType entityType, FileType fileType) {
        if (entityType == EntityType.UNKNOWN) {
            return false;
        }
        final String[] tokens = path.split("/");
        return switch (fileType) {
            // path should include timestamp in expected format
            case GENERIC, LEAD_FILE -> findTimestampPosition(tokens) > 0 && tokens.length >= 5;
            case LATEST_LEAD_FILE -> tokens.length >= 4;
        };
    }

    @Nonnull
    private String parsePath(String path, EntityType entityType, FileType fileType) {
        final String[] tokens = path.split("/");
        StringBuilder newPath = new StringBuilder();
        switch (fileType) {
            case LATEST_LEAD_FILE -> {
                // All latest lead files should be moved with the HF layout under .jfrog folder
                newPath.append(ML_LAYOUT_LATEST_LEAD_FILES_FOLDER).append(File.separator);
                newPath.append(path);
            }
            case LEAD_FILE -> {
                // returning the same path with joined revision and timestamp. Revision position is 2 from the end.
                for (int i = 0; i < tokens.length - POSITION_OF_REVISION_FROM_END; i++) {
                    newPath.append(tokens[i]).append(File.separator);
                }
                newPath.append(getRevisionWithTimestampForLeadFile(tokens));
                newPath.append(File.separator).append(entityType.getLeadFileName());
            }
            case GENERIC -> {
                // model/dataset should be added to the path after timestamp. Find the position of the timestamp.
                int timestampPosition = findTimestampPosition(tokens);
                for (int i = 0; i < timestampPosition - 1; i++) {
                    newPath.append(tokens[i]).append(File.separator);
                }
                newPath.append(tokens[timestampPosition - 1]).append("_").append(tokens[timestampPosition])
                        .append(File.separator);
                newPath.append(entityType.name().toLowerCase()).append(File.separator);
                for (int i = timestampPosition + 1; i < tokens.length - 1; i++) {
                    newPath.append(tokens[i]).append(File.separator);
                }
                newPath.append(tokens[tokens.length - 1]);
            }
        }
        return newPath.toString();
    }

    private int findTimestampPosition(String[] tokens) {
        int timestampPosition = -1;
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].matches(DATE_TIME_PATTERN)) {
                timestampPosition = i;
                break;
            }
        }
        return timestampPosition;
    }

}
