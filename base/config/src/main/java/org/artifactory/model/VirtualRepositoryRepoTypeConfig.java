package org.artifactory.model;

import lombok.Data;
import org.artifactory.repo.virtual.VirtualCacheConfig;
import org.jfrog.common.config.diff.DiffAtomic;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Uriah Levy
 */
@Data
@GenerateDiffFunction
public class VirtualRepositoryRepoTypeConfig implements SpecificTypeRepositoryConfig {
    @NotUnicode
    private String defaultDeploymentRepoRef; // ex-id-ref
    private boolean artifactoryRequestsCanRetrieveRemoteArtifacts;
    private VirtualCacheConfig virtualCacheConfig = new VirtualCacheConfig();
    @DiffAtomic
    private List<@NotUnicode String> repositoryRefs = new ArrayList<>();

    public VirtualRepositoryRepoTypeConfig() {
    }

    public VirtualRepositoryRepoTypeConfig(VirtualRepositoryRepoTypeConfig virtualRepositoryRepoTypeConfig) {
        this.defaultDeploymentRepoRef = virtualRepositoryRepoTypeConfig.getDefaultDeploymentRepoRef();
        this.artifactoryRequestsCanRetrieveRemoteArtifacts =
                virtualRepositoryRepoTypeConfig.isArtifactoryRequestsCanRetrieveRemoteArtifacts();
        this.virtualCacheConfig = new VirtualCacheConfig(virtualRepositoryRepoTypeConfig.getVirtualCacheConfig());
        this.repositoryRefs = new ArrayList<>(virtualRepositoryRepoTypeConfig.getRepositoryRefs());
    }
}
