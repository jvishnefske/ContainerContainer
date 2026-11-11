package org.artifactory.projects;

import com.google.common.base.Strings;
import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.util.AolUtils;
import org.artifactory.util.CollectionUtils;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;

/**
 * @author BarakH
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProjectsUtils {

    public static final String PROJ_LIMIT_MSG =
            "Project storage quota exceeded. If you require more storage, contact your Platform Admin.";
    public static final String PROJ_SOFT_LIMIT_MSG =
            "Project storage quota exceeded. To review you storage status, contact your Platform Admin.";
    private static final Pattern PROJECT_REPO_KEY_PATTERN = Pattern.compile("^([a-zA-Z0-9]*)-(.*)");

    public static boolean isDefaultProject(@Nullable String project) {
        return StringUtils.isEmpty(project) || DEFAULT_PROJECT.equalsIgnoreCase(project);
    }

    /**
     * The method checks if the provided projectKey representing 'Unassigned' context (i.e resources that are not
     * assigned to any project)
     */
    public static boolean isUnassignedContext(@Nullable String projectKey) {
        return StringUtils.isEmpty(projectKey) || ProjectsService.UNASSIGNED.equalsIgnoreCase(projectKey);
    }

    /**
     * @throws IllegalArgumentException if the given project does not contain all the virtual repositories
     */
    public static void assertVirtualRepositoriesAreInProject(@NonNull ProjectsService projectsService,
            @Nullable String projectKey, @Nullable List<String> virtualReposList) throws IllegalArgumentException {
        if (isDefaultProject(projectKey) || CollectionUtils.isNullOrEmpty(virtualReposList)) {
            return;
        }
        Set<String> projectRepositories = projectsService.getProjectRepositoriesKeys(projectKey, false);
        if (!projectRepositories.containsAll(virtualReposList)) {
            throw new IllegalArgumentException(
                    "Virtual repository must include only repositories from the same project it was created with");
        }
    }

    @Nullable
    public static String getProjectKeyFromRepoKey(@NonNull String repoKey) {
        Matcher matcher = PROJECT_REPO_KEY_PATTERN.matcher(repoKey);
        return matcher.matches() ? matcher.group(1) : null;
    }

    /**
     * Indicating whether to use project storage quota SOFT limit or not
     * always true if in AOL environment
     *
     * @param isSoftLimitConfiguredValue - the value taken from project entity
     */
    public static boolean isStorageQuotaSoftLimit(boolean isSoftLimitConfiguredValue) {
        return isSoftLimitConfiguredValue || AolUtils.isAol();
    }

    public static boolean handleStatusUpdateByQuotaLimitType(MutableStatusHolder statusHolder, boolean isSoftLimit,
            Logger log) {
        if (ProjectsUtils.isStorageQuotaSoftLimit(isSoftLimit)) {
            log.warn(PROJ_SOFT_LIMIT_MSG);
            return false;
        } else {
            statusHolder.error(PROJ_LIMIT_MSG, HttpStatus.SC_REQUEST_TOO_LONG, log);
            return true;
        }
    }

    /**
     * if project key is null, then normalized it to be default project
     *
     * @return normalized project key
     */
    @NonNull
    public static String getNormalizedProjectKey(@Nullable String projectKey) {
        return Strings.isNullOrEmpty(projectKey) ? DEFAULT_PROJECT : projectKey;
    }
}
