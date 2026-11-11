package org.artifactory.storage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.storage.cache.entity.StorageDbCacheEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.storage.service.ProjectSizeEntry.CalcStatus.PROJECT_ERROR_NO_REPOS;
import static org.artifactory.storage.service.ProjectSizeEntry.CalcStatus.PROJECT_ERROR_NUM_OF_ARTIFACTS_IN_REPO_EXCEEDED;
import static org.artifactory.storage.service.ProjectSizeEntry.CalcStatus.PROJECT_ERROR_NUM_OF_REPOS_EXCEEDED;
import static org.artifactory.storage.service.ProjectSizeEntry.CalcStatus.PROJECT_ERROR_INELIGIBLE_NAME;
import static org.artifactory.storage.service.ProjectSizeEntry.CalcStatus.SERVER_ERROR;

/**
 * Implementation of ProjectBinaryStorageService
 *
 * @author Evgeny Kurtser
 */
@Slf4j
@Service
public class ProjectBinarySizeServiceImpl implements ProjectBinarySizeService {

    private static final String ENTITY_TYPE_PROJECT = "PROJECT";

    private final StorageCacheService<ProjectSizeEntry> storageCacheService;
    private final ProjectsService projectsService;
    private final RepositoryService repositoryService;
    private final StorageSummaryService storageSummaryService;
    private final NodesDao nodesDao;

    private final int executionDelayFactor;
    private final int maxRepos;
    private final long maxArtifacts;
    private final LongPredicate withinLimit;
    private final Predicate<String> eligibleProjectName;
    private final ObjectMapper mapper;

    @Autowired
    public ProjectBinarySizeServiceImpl(
            StorageCacheService<ProjectSizeEntry> storageCacheService,
            ProjectsService projectsService,
            RepositoryService repositoryService,
            StorageSummaryService storageSummaryService,
            NodesDao nodesDao) {
        this.storageCacheService = storageCacheService;
        this.projectsService = projectsService;
        this.repositoryService = repositoryService;
        this.storageSummaryService = storageSummaryService;
        this.nodesDao = nodesDao;

        int factor = ConstantValues.projectBinaryStorageExecutionDelayFactor.getInt();
        if (factor <= 0) {
            log.warn("Execution delay factor must be a positive integer and not {}. It will be set to 1", factor);
            factor = 1;
        }
        this.executionDelayFactor = factor;

        maxRepos = ConstantValues.projectBinaryStorageMaxRepos.getInt();
        maxArtifacts = ConstantValues.projectBinaryStorageMaxArtifacts.getLong();
        withinLimit = count -> count <= maxArtifacts;
        eligibleProjectName = projectName -> !DEFAULT_PROJECT.equals(projectName);
        mapper = new ObjectMapper();
    }

    @Override
    @Nullable
    public ProjectBinarySizeInfo getProjectBinarySizeInfo(@NonNull String projectKey) {
        return storageCacheService.get(ENTITY_TYPE_PROJECT, projectKey, ProjectSizeEntry.class)
                .map(cacheEntry -> {
                    ProjectSizeEntry value = cacheEntry.value();
                    if (value.isSuccess()) {
                        log.debug("Retrieved cached binary storage for project {}: {} bytes",
                                projectKey, value.projectSize());
                    }
                    return new ProjectBinarySizeInfo(cacheEntry);
                }).orElse(null);
    }

    @Override
    public List<ProjectBinarySizeInfo> getAllProjectsBinaryStorage() {
        return storageCacheService.get(ENTITY_TYPE_PROJECT, ProjectSizeEntry.class)
                .orElse(Collections.emptyList()).stream()
                .map(ProjectBinarySizeInfo::new)
                .toList();
    }


    @Override
    public int calculateAllProjectsSizes() {
        log.info("Starting binary storage size calculation for all projects");
        long startTime = System.currentTimeMillis();
        int processedCount = 0;
        int totalCount = 0;
        boolean interrupted = false;
        Set<String> allegeableProjects = filterProjects();
        deleteRemovedProjectsFromCache(allegeableProjects);
        Set<String> skippedProjects = new HashSet<>(allegeableProjects.size());

        for (String projectName : allegeableProjects) {
            totalCount++;
            // The loop may be interrupted due to execution timeout.
            // In that case, we log a warning and store the error message in the rest of the unhandled projects.
            if (interrupted || Thread.currentThread().isInterrupted()) {
                skippedProjects.add(projectName);
                interrupted = true;
                continue;
            }

            // Calculate size for single project, take execution time and delay the same amount of time
            // to avoid overloading the database.
            try {
                log.debug("Start calculating binary storage for project {}", projectName);
                long start = System.currentTimeMillis();
                calculateProjectSize(projectName);
                // Report execution time and sleep before next execution
                long executionTimeMillis = System.currentTimeMillis() - start;
                if (totalCount < allegeableProjects.size()) {
                    long sleepIntervalMillis = executionTimeMillis / executionDelayFactor;
                    log.debug("Finish calculation of binary storage for project {}. It took {} seconds. " +
                                    "Next calculation will start after {} seconds", projectName,
                            String.format("%.1f", executionTimeMillis / 1000.0),
                            String.format("%.1f", sleepIntervalMillis / 1000.0));
                    TimeUnit.MILLISECONDS.sleep(sleepIntervalMillis);
                } else {
                    log.debug("Finish last calculation of binary storage for project {}. It took {} seconds.",
                            projectName, String.format("%.1f", executionTimeMillis / 1000.0));
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            processedCount++;
        }

        if (!skippedProjects.isEmpty()) {
            log.warn("Size calculation of project {} bypassed due to interruption",
                    String.join(", ", skippedProjects));
        }
        log.info("Completed binary storage calculation for {} projects. It took {} seconds",
                processedCount, String.format("%.1f", (System.currentTimeMillis() - startTime) / 1000.0));
        return processedCount;
    }

    @Override
    public int cleanup(long since) {
        return storageCacheService.removeOlderThan(ENTITY_TYPE_PROJECT, since);
    }

    /**
     * Filter projects by artifact count to avoid processing projects with too many artifacts.
     * The max artifact count is defined by the {@linkplain ConstantValues#projectBinaryStorageMaxArtifacts}
     * configuration property.
     * Default project is excluded as well.
     *
     * @return Set of project keys that are within the artifact count limit
     */
    @NonNull
    private Set<String> filterProjects() {
        try {
            StorageSummaryInfo summaryInfo = storageSummaryService.getLocalCache();
            Map<String, Long> projectsTotalFilesCount = summaryInfo.getProjectsTotalFilesCount();
            return projectsTotalFilesCount.entrySet().stream()
                    // Handle projects exceeding the limit
                    .peek(this::peekProject)
                    .filter(entry -> withinLimit.test(entry.getValue()))
                    .filter(entry -> eligibleProjectName.test(entry.getKey()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        } catch (CacheUnAvailableException e) {
            log.warn("Failed to retrieve storage summary info", e);
            return Collections.emptySet();
        }
    }

    /**
     * Delete cached projects that no longer exist.
     * @param projects Set of existing project keys.
     */
    private void deleteRemovedProjectsFromCache(Set<String> projects) {
        // Get all cached projects
        Optional<List<StorageCacheService.StorageCacheEntry<ProjectSizeEntry>>> cachedProjects =
                storageCacheService.get(ENTITY_TYPE_PROJECT, ProjectSizeEntry.class);
        // Find and remove cached projects that no longer exist
        cachedProjects.ifPresent(cacheEntries -> {
            Set<String> projectsToRemove = cacheEntries.stream()
                    .map(StorageCacheService.StorageCacheEntry::key)
                    .filter(projectKey -> !projects.contains(projectKey))
                    .collect(Collectors.toSet());
            if (!projectsToRemove.isEmpty()) {
                log.debug("Removing {} cached projects as they're no longer eligible: {}",
                        projectsToRemove.size(), projectsToRemove);
                storageCacheService.remove(ENTITY_TYPE_PROJECT, projectsToRemove);
            }
        });
    }

    private void peekProject(Map.Entry<String, Long> entry) {
        if (!withinLimit.test(entry.getValue())) {
            String message = String
                    .format("Skipping project %s with %,d artifacts (exceeds max of %,d)",
                            entry.getKey(), entry.getValue(), maxArtifacts);
            log.info(message);
            storeSafely(entry.getKey(),
                    ProjectSizeEntry.failure(PROJECT_ERROR_NUM_OF_ARTIFACTS_IN_REPO_EXCEEDED));
            return;
        }
        if (!eligibleProjectName.test(entry.getKey())) {
            String message = String
                    .format("Skipping project %s (ineligible name: %s)",
                            entry.getKey(), DEFAULT_PROJECT);
            log.info(message);
            storeSafely(entry.getKey(),
                    ProjectSizeEntry.failure(PROJECT_ERROR_INELIGIBLE_NAME));
        }
    }


    /**
     * Calculate binary storages size for a single project and store the result in persistent cache.
     * If the project has no repositories or exceeds limits, size is set to 0.
     *
     * @param projectName the project key
     */
    private void calculateProjectSize(String projectName) {
        ProjectSizeEntry res = null;
        try {
            // Get project repositories
            Set<String> repos = projectsService.getProjectRepositoriesKeys(projectName, false);
            repos = filterRepos(repos);
            if (repos.isEmpty()) {
                String message = String.format("No repositories found for project %s", projectName);
                log.debug(message);
                res = ProjectSizeEntry.failure(PROJECT_ERROR_NO_REPOS);
                return;
            }
            if (repos.size() > maxRepos) {
                String message = String.format("Number of repositories %,d exceeds max of %,d for project %s",
                        repos.size(), maxRepos, projectName);
                log.info(message);
                res = ProjectSizeEntry.failure(PROJECT_ERROR_NUM_OF_REPOS_EXCEEDED);
                return;
            }
            // Calculate distinct binary size for given repositories
            long binarySize = nodesDao.getReposBinarySize(repos);
            log.info("Calculated binary storage size for project {}: {} bytes", projectName, binarySize);
            res = ProjectSizeEntry.success(binarySize);
        } catch (Exception e) {
            log.error("Error calculating binary storage size for project {}: {}", projectName, e.getMessage(), e);
            res = ProjectSizeEntry.failure(SERVER_ERROR);
        } finally {
            // Store result
            storeSafely(projectName, res);
        }
    }

    /**
     * The method filters out ineligible repositories from the given set.
     * An ineligible repository is defined as:
     * 1. A repository that is not real (i.e., a virtual repository).
     * 2. A remote repository.
     *
     * @param repoKeys the original set of repository keys
     * @return a new set with the filtered repository keys
     */
    @NonNull
    private Set<String> filterRepos(@NonNull Set<String> repoKeys) {
        return repoKeys.stream()
                .map(repositoryService::getRepoConfigByKey)
                .filter(Objects::nonNull)
                .filter(repoConfig -> repoConfig.isReal() && repoConfig.getRepoType() != RepoDetailsType.REMOTE)
                .map(RepoConfig::getKey)
                .collect(Collectors.toSet());
    }

    private void storeSafely(@NonNull String projectName, ProjectSizeEntry newValue) {
        if (newValue == null) {
            log.error("Cannot store null binary storage size for project {}", projectName);
            return;
        }
        try {
            storageCacheService.putConditionally(ENTITY_TYPE_PROJECT, projectName, newValue,
                    overwriteExisting(projectName, newValue));
        } catch (Exception e) {
            log.error("Failed to store binary storage size for project {}: {}", projectName, e.getMessage(), e);
        }
    }

    // Don't overwrite existing valid value or project error with server error
    private @NonNull Predicate<@NonNull StorageDbCacheEntity> overwriteExisting(
            @NonNull String projectName, ProjectSizeEntry newValue) {
        return existing -> {
            if (newValue.status() == SERVER_ERROR) {
                try {
                    ProjectSizeEntry existingEntry = mapper.readValue(existing.value(), ProjectSizeEntry.class);
                    if (existingEntry.status() != SERVER_ERROR) {
                        log.trace("Existing value {} of project {} won't be overwritten with server error",
                                existing.value(), projectName);
                        return false;
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Unparsable existing project size entry of project {} will be overwritten: {}",
                            projectName, e.getMessage());
                }
            }
            return true;
        };
    }
}