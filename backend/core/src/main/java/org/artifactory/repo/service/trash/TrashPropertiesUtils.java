package org.artifactory.repo.service.trash;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.repo.RepoPath;

import static org.artifactory.repo.trash.TrashService.PROP_DELETED_BY;
import static org.artifactory.repo.trash.TrashService.PROP_TRASH_TIME;

/**
 * Utility class for handling trash-related properties operations.
 * This centralizes the logic for cleaning up trash properties that is used in both
 * regular artifact restoration (TrashServiceImpl) and evidence restoration.
 */
@Slf4j
@UtilityClass
public class TrashPropertiesUtils {

    /**
     * Cleans up trash-related properties from the given repository path recursively.
     * This removes properties like trash.time and trash.deletedBy that are added when items are moved to trash.
     * 
     * This method centralizes the logic that was previously duplicated in TrashServiceImpl.restore()
     * and evidence restoration code.
     *
     * @param propertiesService The properties service to use for property operations
     * @param repoPath The repository path to clean up trash properties from
     */
    public static void cleanupTrashProperties(
            @NonNull PropertiesService propertiesService, 
            @NonNull RepoPath repoPath) {
        log.debug("Cleaning up trash properties from: {}", repoPath);
        propertiesService.deletePropertyRecursively(repoPath, PROP_TRASH_TIME, false);
        propertiesService.deletePropertyRecursively(repoPath, PROP_DELETED_BY, false);
    }
}
