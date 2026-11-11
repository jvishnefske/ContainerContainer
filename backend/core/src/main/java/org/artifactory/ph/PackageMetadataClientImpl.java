package org.artifactory.ph;

import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.service.PackageMetadataClient;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.repo.PackageType;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.jfrog.metadata.client.MetadataClient;
import org.jfrog.metadata.client.model.MetadataPackageImpl;

import java.util.List;


public class PackageMetadataClientImpl implements PackageMetadataClient {
    private final InternalRepositoryService repositoryService;

    public PackageMetadataClientImpl(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public List<PackageArtifact> search(String repoKey, String searchTerms, String limit) {
        RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(repoKey);
        if (repoConfig == null) {
            return List.of();
        }

        PackageType type = PackageType.valueOf(repoConfig.getPackageType().name().toUpperCase());
        List<MetadataPackageImpl> packages = getMetadataClient().packages().search(repoKey, searchTerms, limit);
        return packages.stream()
                .map(pkg -> PackageArtifactMapper.artifact(pkg, repoKey, type))
                .flatMap(List::stream)
                .toList();
    }

    private MetadataClient getMetadataClient() {
        return ContextHelper.get().beanForType(MetadataEventService.class).getMetadataClient();
    }
}