package org.artifactory.repo;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.repo.federated.FederatedMember;

import java.util.List;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.util.FederatedMemberUtils.resolveUrl;

/**
 * Federated repository configuration
 *
 * @author Gidi Shabat
 * @author Andrei Komarov
 * @see FederatedRepoDescriptor
 */
@Data
@EqualsAndHashCode(callSuper = false)
@JsonFilter("typeSpecificFilter")
@NoArgsConstructor
public class FederatedRepositoryConfigurationImpl extends LocalRepositoryConfigurationImpl
        implements FederatedRepositoryConfiguration {

    public static final String TYPE = "federated";
    @IncludeTypeSpecific(repoType = FEDERATED)
    private String proxy;
    @IncludeTypeSpecific(repoType = FEDERATED)
    private boolean disableProxy;

    public FederatedRepositoryConfigurationImpl(FederatedRepoConfig federatedRepoConfig) {
        super(federatedRepoConfig, TYPE);
        List<FederatedMember> descriptorMembers = federatedRepoConfig.getRepoTypeConfig().getFederatedMembers();
        if (descriptorMembers != null && !descriptorMembers.isEmpty()) {
            setMembers(descriptorMembers.stream()
                    .map(this::toFederatedMemberConfig)
                    .toList());
        } else {
            setPropertySets(Lists.newArrayList());
        }
        disableProxy = federatedRepoConfig.getRepoTypeConfig().isDisableProxy();
        if (!disableProxy) {
            proxy = federatedRepoConfig.getRepoTypeConfig().getProxyRef();
        }
    }

    private FederatedMemberConfiguration toFederatedMemberConfig(FederatedMember a) {
        return new FederatedMemberConfiguration(resolveUrl(a), a.getMode(), a.isEnabled());
    }

    @Override
    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    @Override
    public boolean isDisableProxy() {
        return disableProxy;
    }

    public void setDisabledProxy(boolean disableProxy) {
        this.disableProxy = disableProxy;
    }

    /**
     * Override to ensure setFederatedMembers() uses the same field as setMembers().
     * This keeps programmatic code and JSON API consistent.
     */
    @Override
    public void setFederatedMembers(List<FederatedMemberConfiguration> members) {
        setMembers(members);
    }

}
