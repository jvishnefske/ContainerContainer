package org.artifactory.pipelines;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.system.SystemRepoConfigFactory;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.rest.exception.RestException;
import org.jfrog.access.client.project.ProjectSlimEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.artifactory.projects.ProjectsService.DEFAULT_ENVIRONMENT;
import static org.artifactory.repo.system.SystemRepos.PIPEINFO_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.PIPEINFO;

/**
 * @author Alexei Vainshtein
 */
@Slf4j
@Service
public class PipelinesServiceImpl implements InternalPipelinesService {

    private ProjectsService projectsService;
    private AuthorizationService authService;
    private SecurityService securityService;
    private InternalRepositoryService internalRepositoryService;
    private SystemRepoConfigFactory systemRepoConfigFactory;

    @Autowired
    public void setAuthService(AuthorizationService authService) {
        this.authService = authService;
    }

    @Autowired
    public void setInternalRepositoryService(InternalRepositoryService internalRepositoryService) {
        this.internalRepositoryService = internalRepositoryService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setProjectsService(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Autowired
    public void setSystemRepoConfigFactory(SystemRepoConfigFactory systemRepoConfigFactory) {
        this.systemRepoConfigFactory = systemRepoConfigFactory;
    }

    @Override
    public boolean isPipeInfoRepo(String repoName) {
        return internalRepositoryService.isPipeInfoRepository(repoName);
    }

    @Override
    public void saveContent(String filePath, String pipeRepoKey, String projectKey, InputStream inputStream)
            throws IOException {
        validateProvidedParams(pipeRepoKey, projectKey);
        String finalPipeRepoName = getAndCreatePipeRepo(pipeRepoKey, projectKey);
        RepoPath fileRepoPath = RepoPathFactory.create(finalPipeRepoName, filePath);
        log.debug("Saving {} to {}", filePath, finalPipeRepoName);
        if (!authService.canDeploy(fileRepoPath)) {
            String message = String.format("User %s is not permitted to use this api",
                    securityService.currentUsername());
            log.error(message);
            throw new ForbiddenException(message);
        }
        try (inputStream) {
            StatusHolder statusHolder = internalRepositoryService.deploy(fileRepoPath, inputStream);
            throwIfError(statusHolder);
            String path = fileRepoPath.toPath();
            log.debug("File saved to {}", path);
        }
    }

    public static void throwIfError(StatusHolder statusHolder) {
        if (statusHolder.isError()) {
            throw new RestException(statusHolder.getStatusCode(), statusHolder.getStatusMsg());
        }
    }

    /**
     * Finds the {@link RepoType#PipeInfo} repository key of the given project key.
     *
     * @param projectKey - The project key
     * @return the Pipe Info repository key of the given project.
     */
    String createAndRetrieveRepoName(String projectKey) {
        final String requiredRepoName = getRepoName(projectKey);
        log.debug("Looking for pipe-info repository {} for project key {}.", requiredRepoName, projectKey);
        createPipeInfoRepoIfNeeded(requiredRepoName, projectKey);
        return requiredRepoName;
    }

    /**
     * Returns the repository name based on the provided project key
     */
    String getRepoName(String projectKey) {
        if (ProjectsUtils.isDefaultProject(projectKey)) {
            return PIPEINFO_REPO_KEY;
        }
        return projectKey + PIPE_INFO_REPO_SUFFIX;
    }

    /**
     * Creates a {@link RepoType#PipeInfo} repository if needed
     *
     * @param repoKey    - The repo name to create
     * @param projectKey - The project key the created repo will be assigned.
     */
    void createPipeInfoRepoIfNeeded(String repoKey, String projectKey) {
        RepoConfig repoConfig = internalRepositoryService.getRepositoryConfigByKey(repoKey);
        if (repoConfig == null) {
            createRepoAndAddToProject(repoKey, projectKey);
            return;
        }
        validateExistingRepo(repoKey, repoConfig);
    }

    /**
     * Validates the provided repository is of {@link RepoType#PipeInfo} type and is local.
     */
    void validateExistingRepo(String repoKey, RepoConfig repoConfig) {
        if (repoConfig instanceof LocalConfig && repoConfig.getPackageType().isPipeInfo()) {
            log.debug("Repository {} already exists", repoKey);
            return;
        }
        // We have a problem - Repo name is taken
        throwException("Repository key " + repoKey + " is already taken");
    }

    /**
     * Creates the repo and assigns the repository to the project key
     */
    void createRepoAndAddToProject(String repoKey, String projectKey) {
        log.debug("Repository {} not found, trying to create", repoKey);
        securityService.doAsSystem(() -> {
            log.debug("Creating repo {}", repoKey);
            systemRepoConfigFactory.buildSystemRepo(PIPEINFO, repoKey);
            if (!ProjectsUtils.isDefaultProject(projectKey)) {
                log.debug("Adding the repo {} to project key {}", repoKey, projectKey);
                projectsService.addProjectRepository(repoKey, projectKey, Set.of(DEFAULT_ENVIRONMENT));
            }
        });
    }

    /**
     * Validate if the pipe repo matches the projectKey. If so, and the pipe repo doesn't exists, it will create the
     * repository
     *
     * @param pipeRepo   - The repo name (can be null)
     * @param projectKey - The projectKey name (can be null)
     * @return the final pipeline repository name.
     */
    String getAndCreatePipeRepo(String pipeRepo, String projectKey) {
        // First case, projectKey configured, repo is empty.
        // Searching by projectKey
        if (StringUtils.isBlank(pipeRepo) && StringUtils.isNotBlank(projectKey)) {
            return createAndRetrieveRepoName(projectKey);
        }

        if (StringUtils.isNotBlank(pipeRepo) && !pipeRepo.endsWith(InternalPipelinesService.PIPE_INFO_REPO_SUFFIX)) {
            throwException(String.format("Repository %s doesn't meet the pipeline info convention", pipeRepo));
        }
        // Second case: projectKey and repo not empty.
        if (StringUtils.isNotBlank(pipeRepo) && StringUtils.isNotBlank(projectKey)) {
            createRepoBasedOnProjectAndRepo(pipeRepo, projectKey);
            return pipeRepo;
        }
        createPipeRepoIfNeededBasedOnRepo(pipeRepo);
        if (StringUtils.isBlank(pipeRepo)) {
            pipeRepo = PIPEINFO_REPO_KEY;
        }
        return pipeRepo;
    }

    void createPipeRepoIfNeededBasedOnRepo(String pipeRepo) {
        // Third case: no project but with repo. Need to check if repo exists, if not, create and put to a project.
        if (StringUtils.isEmpty(pipeRepo)) {
            pipeRepo = PIPEINFO_REPO_KEY;
        }
        if (PIPEINFO_REPO_KEY.equals(pipeRepo)) {
            log.debug("Repository {} part of the default project", pipeRepo);
            createPipeInfoRepoIfNeeded(pipeRepo, ProjectsService.DEFAULT_PROJECT);
            return;
        }

        String projectKeyFromRepoKey = ProjectsUtils.getProjectKeyFromRepoKey(pipeRepo);
        ProjectSlimEntity projectSpec = projectsService.getProjectSpec(projectKeyFromRepoKey);
        if (projectSpec != null) {
            createPipeInfoRepoIfNeeded(pipeRepo, projectSpec.getProjectKey());
            return;
        }
        String message = "No project matches the project key " + projectKeyFromRepoKey + " prefix";
        throwException(message);
    }

    /**
     * Creates the repository, if needed, based on the provided repository name and project key.
     * Checks that the provided repo matches the provided project key.
     */
    void createRepoBasedOnProjectAndRepo(String pipeRepo, String projectKey) {
        //  Need to check that are matching
        if (!pipeRepo.equals(projectKey + PIPE_INFO_REPO_SUFFIX)) {
            throwException(String.format("Repository %s doesn't belong to project key %s", pipeRepo, projectKey));
        }
        Set<String> projectRepos = projectsService.getProjectRepositoriesKeys(projectKey, false);
        if (projectRepos.contains(pipeRepo)) {
            log.debug("Found repository {} that belongs to project key {}", pipeRepo, projectKey);
            return;
        }
        if (internalRepositoryService.getRepositoryConfigByKey(pipeRepo) != null) {
            String message = "Found repo " + pipeRepo + ", however, this repo doesn't belong to project " + projectKey;
            throwException(message);
        }
        createRepoAndAddToProject(pipeRepo, projectKey);
    }

    /**
     * Validate the params provided
     *
     * @param pipeRepo   - The repository key provided via the api
     * @param projectKey - The project key provided
     */
    void validateProvidedParams(String pipeRepo, String projectKey) {
        validateProjectKey(projectKey);
        validatePipeRepo(pipeRepo);
    }

    private void validatePipeRepo(String pipeRepo) {
        if (StringUtils.isBlank(pipeRepo)) {
            return;
        }
        if (!pipeRepo.endsWith(InternalPipelinesService.PIPE_INFO_REPO_SUFFIX)) {
            String message = String.format("Pipe repo %s is not matching the convention of pipe info repos", pipeRepo);
            throwException(message);
        }

        String projectKeyFromRepoKey = ProjectsUtils.getProjectKeyFromRepoKey(pipeRepo);
        if (StringUtils.isBlank(projectKeyFromRepoKey)) {
            String message = String.format("Pipe repo %s is not matching the convention of pipe info repos", pipeRepo);
            throwException(message);
        }
    }

    private void validateProjectKey(String projectKey) {
        if (StringUtils.isBlank(projectKey)) {
            return;
        }

        ProjectSlimEntity projectSpec = projectsService.getProjectSpec(projectKey);
        if (projectSpec == null) {
            String message = "No project matches the project key " + projectKey + " prefix";
            throwException(message);
        }
    }

    private void throwException(String message) {
        log.error(message);
        throw new IllegalArgumentException(message);
    }
}
