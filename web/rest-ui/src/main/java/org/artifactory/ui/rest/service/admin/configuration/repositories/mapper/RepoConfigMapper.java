package org.artifactory.ui.rest.service.admin.configuration.repositories.mapper;

import lombok.RequiredArgsConstructor;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoReplicationUtil;
import org.artifactory.repo.RepoStatDto;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.ProjectRepo;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigResponse;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.ProjectRepoHelper;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectResourceStatus;
import org.jfrog.access.client.project.ProjectSlimEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;

@Component
@RequiredArgsConstructor
public class RepoConfigMapper {

    private final ProjectsService projectsService;
    private final PackageSpecification packageSpecification;
    private final FederatedRepoService federatedRepoService;
    private final CentralConfigService centralConfigService;
    private final ProjectRepoHelper projectRepoHelper;

    public RepoConfigResponse toResponse(RepoConfig repoConfig, RepoStatDto repoStatDto,
                                         Map<String, List<ProjectRepo>> allProjectRepos,
                                         Set<String> inActiveFederatedRepos) {
        String repoKey = repoConfig.getKey();
        RepoConfigResponse repoConfigResponse = new RepoConfigResponse();
        String projectKey = projectsService.getProjectKeyOfRepository(repoKey);

        handleCommonConfiguration(repoConfig, repoStatDto, repoConfigResponse, inActiveFederatedRepos);
        handleRepoTypeSpecific(repoConfig, repoConfigResponse, projectKey);
        handleProjectConfiguration(repoKey, projectKey, allProjectRepos, repoConfigResponse);

        return repoConfigResponse;
    }

    private void handleCommonConfiguration(RepoConfig repoConfig, RepoStatDto repoStatDto,
                                           RepoConfigResponse repoConfigResponse,
                                           Set<String> inActiveFederatedRepos) {
        String repoKey = repoConfig.getKey();
        RepoDetailsType repoType = repoConfig.getRepoType();
        RepoType packageType = repoConfig.getPackageType();
        repoConfigResponse.setRepoKey(repoKey);
        mapRepoTypeAndFederationAttribute(repoConfig, repoConfigResponse);
        repoConfigResponse.setPackageType(packageType);
        repoConfigResponse.setLastActivity(getLastActivity(repoStatDto));
        repoConfigResponse.setInactive(!isActive(repoConfig, inActiveFederatedRepos));

        org.artifactory.common.repo.RepoType type = mapRepoType(repoType);
        if (type != null) {
            repoConfigResponse.setHasReindexAction(packageSpecification.hasReindex(type, packageType));
        }
        repoConfigResponse.setHasMigrationAction(packageSpecification.hasMigrationAction(repoConfig));
    }

    private void handleProjectConfiguration(String repoKey, String projectKey,
                                            Map<String, List<ProjectRepo>> allProjectRepos,
                                            RepoConfigResponse response) {
        ProjectSlimEntity projectEntity = projectsService.getProjectSpec(projectKey);
        response.setProjectKey(projectKey);
        response.setProjectName(projectEntity != null ? projectEntity.getName() : null);
        ProjectResourceEntity assignedProject = projectRepoHelper.getAssignedProject(repoKey, projectKey,
                allProjectRepos);
        response.setEnvironments(assignedProject != null ? assignedProject.getEnvironments()
                : projectRepoHelper.getEnvironmentsForDefaultProject(repoKey));
        if (assignedProject != null) {
            response.setTarget(assignedProject.getResourceStatus() != null &&
                    assignedProject.getResourceStatus().isTarget());
            response.setSharedWithProjects(assignedProject.getSharedWithProjects());
            response.setSharedReadOnly(assignedProject.isSharedReadOnly());
            boolean sharedWithAllProjects = assignedProject.getSharedWithProjects() != null &&
                    ProjectResourceStatus.SHARED_WITH_ALL == assignedProject.getResourceStatus();
            response.setShareWithAllProjects(sharedWithAllProjects);
        }
    }

    private void handleRepoTypeSpecific(RepoConfig repoConfig, RepoConfigResponse response, String projectKey) {
        RepoDetailsType repoType = repoConfig.getRepoType();
        switch (repoType) {
            case REMOTE -> handleRemote((RemoteRepoConfig) repoConfig, response);
            case LOCAL -> handleLocal((LocalRepoConfig) repoConfig, response, projectKey);
            case FEDERATED -> handleFederated((FederatedRepoConfig) repoConfig, response);
            case VIRTUAL -> handleVirtual((VirtualRepoConfig) repoConfig, response);
            case RELEASE_BUNDLE -> handleReleaseBundle(response);
        }
    }

    private void handleRemote(RemoteRepoConfig repoConfig, RepoConfigResponse response) {
        response.setSmart(repoConfig.isSmart());
        response.setUrl(repoConfig.getRepoTypeConfig().getUrl());
        response.setHasEnabledReplication(hasReplication(repoConfig));
    }

    private void handleLocal(LocalRepoConfig repoConfig, RepoConfigResponse response, String projectKey) {
        response.setReplications(hasReplication(repoConfig));
        boolean isProjectBuildInfoRepoFederated = federatedRepoService
                .isProjectBuildInfoRepoFederated(projectKey);
        response.setEnableConvertToFederated(!isProjectBuildInfoRepoFederated);
    }

    private void handleFederated(FederatedRepoConfig repoConfig, RepoConfigResponse response) {
        response.setReplications(hasReplication(repoConfig));
    }

    private void handleVirtual(VirtualRepoConfig repoConfig, RepoConfigResponse response) {
        response.setSelectedRepos(repoConfig.getRepoTypeConfig().getRepositoryRefs());
    }

    private void handleReleaseBundle(RepoConfigResponse response) {
        response.setHasReindexAction(false);
    }

    private void mapRepoTypeAndFederationAttribute(RepoConfig repoConfig, RepoConfigResponse response) {
        RepoDetailsType repoType = repoConfig.getRepoType();
        boolean federationAttributeEnabled = ConstantValues.repositoriesFederationsAttributeEnabled.getBoolean();
        if (federationAttributeEnabled && repoType == FEDERATED) {
            response.setRepoType(LOCAL.typeNameLowercase());
        } else {
            response.setRepoType(repoType.typeNameLowercase());
        }
        if (federationAttributeEnabled && repoConfig.isFederated()) {
            response.setFederated(true);
        }
    }

    public boolean hasReplication(RepoConfig repoConfig) {
        return RepoReplicationUtil.hasEnabledReplication(repoConfig, centralConfigService.getDescriptor());
    }

    public boolean isActive(RepoConfig repoConfig, Set<String> inActiveFederatedRepos) {
        return switch (repoConfig) {
            case LocalRepoConfig local -> !local.getRepoTypeConfig().isBlackedOut();
            case RemoteRepoConfig remote -> !remote.getRepoTypeConfig().isBlackedOut() &&
                    !remote.getRepoTypeConfig().isOffline();
            case FederatedRepoConfig federated -> !inActiveFederatedRepos.contains(federated.getKey());
            default -> true;
        };
    }

    private org.artifactory.common.repo.RepoType mapRepoType(RepoDetailsType repoType) {
        if (repoType == null) {
            return null;
        }
        return switch (repoType) {
            case LOCAL -> org.artifactory.common.repo.RepoType.LOCAL;
            case RepoDetailsType.REMOTE -> org.artifactory.common.repo.RepoType.REMOTE;
            case RepoDetailsType.VIRTUAL -> org.artifactory.common.repo.RepoType.VIRTUAL;
            default -> null;
        };
    }

    private long getLastActivity(RepoStatDto repoStatDto) {
        return repoStatDto != null ? Math.max(repoStatDto.getLastDownloaded(), repoStatDto.getLastUploaded()) : 0;
    }
}
