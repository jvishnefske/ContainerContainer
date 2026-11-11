package org.artifactory.model;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.repo.federated.FederatedMember;
import org.jfrog.common.config.diff.DiffIgnore;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import java.util.List;

/**
 * Federated repository config class extends the local repository config with additional params which are relevant
 * for federation
 *
 * @author Uriah Levy
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@GenerateDiffFunction
public class FederatedRepositoryRepoTypeConfig extends LocalRepositoryRepoTypeConfig {
    private List<FederatedMember> federatedMembers = Lists.newArrayList();
    @DiffIgnore
    @MetadataInfo(defaultValue = "0")
    private long modificationDate;
    @NotUnicode
    @MetadataInfo(skip = true)
    private String proxyRef;// ex-xml-idref
    @MetadataInfo(defaultValue = "0")
    private Integer maxNumberOfThreads;
    @MetadataInfo(skip = true)
    private boolean disableProxy;

    public FederatedRepositoryRepoTypeConfig(FederatedRepositoryRepoTypeConfig federatedRepositoryRepoTypeConfig) {
        super(federatedRepositoryRepoTypeConfig);
        federatedRepositoryRepoTypeConfig.getFederatedMembers()
                .forEach(federatedMember -> this.federatedMembers.add(new FederatedMember(federatedMember)));
        this.modificationDate = federatedRepositoryRepoTypeConfig.getModificationDate();
        this.proxyRef = federatedRepositoryRepoTypeConfig.getProxyRef();
        this.maxNumberOfThreads = federatedRepositoryRepoTypeConfig.getMaxNumberOfThreads();
        this.disableProxy = federatedRepositoryRepoTypeConfig.isDisableProxy();
    }
}
