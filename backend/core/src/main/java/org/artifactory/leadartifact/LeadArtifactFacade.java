package org.artifactory.leadartifact;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.leadartifact.detector.LeadArtifactDetector;
import org.artifactory.repo.RepoPath;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LeadArtifactFacade {

    private final Map<RepoType, LeadArtifactDetector> leadArtifactDetectors;
    private final RepositoryService repositoryService;

    public LeadArtifactFacade(
            RepositoryService repositoryService,
            List<? extends LeadArtifactDetector> leadArtifactDetectors) {
        this.repositoryService = repositoryService;
        this.leadArtifactDetectors = leadArtifactDetectors.stream()
                .collect(Collectors.toMap(LeadArtifactDetector::getRepoType,
                        Function.identity()));
    }

    public boolean isLeadArtifact(RepoPath repoPath) {
        return isLead(repoPath, detector -> detector.isLeadArtifact(repoPath));
    }

    public boolean isPackageConsumptionLead(RepoPath repoPath) {
        return isLead(repoPath, detector -> detector.isPackageConsumptionLead(repoPath));
    }

    public Set<String> getLeadArtifactExtensions(RepoPath repoPath) {
        return getRepoDescriptor(repoPath.getRepoKey())
                .map(LocalRepoDescriptor::getType)
                .map(this::getLeadArtifactExtensions)
                .orElse(Set.of());
    }

    public Set<String> getLeadArtifactExtensions(RepoType repoType) {
        return Optional.ofNullable(leadArtifactDetectors.get(repoType))
                .map(LeadArtifactDetector::getLeadArtifactExtensions)
                .orElse(Set.of());
    }

    private boolean isLead(RepoPath repoPath, Predicate<LeadArtifactDetector> leadPredicate) {
        if (!repoPath.isFile() || repoPath.isRoot()) { // <- root is a file :(
            return false;
        }
        return getDetector(repoPath)
                .map(leadPredicate::test)
                .orElse(false);
    }

    private Optional<LeadArtifactDetector> getDetector(RepoPath repoPath) {
        return getRepoDescriptor(repoPath.getRepoKey())
                .map(repoDesc -> leadArtifactDetectors.get(repoDesc.getType()));
    }

    private Optional<LocalRepoDescriptor> getRepoDescriptor(String repoKey) {
        LocalRepoDescriptor repoDescriptor = repositoryService.localFederatedOrCachedRepoDescriptorByKey(repoKey);
        if (repoDescriptor == null) {
            log.debug("Unable to associate artifact lead detector with a local or cache repository: {}", repoKey);
        }
        return Optional.ofNullable(repoDescriptor);
    }
}
