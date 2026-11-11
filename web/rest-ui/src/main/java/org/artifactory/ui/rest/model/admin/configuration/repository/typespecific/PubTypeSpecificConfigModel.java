package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.addon.AddonsManager;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.util.JsonUtil;

import static java.util.Optional.ofNullable;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_PUB_REGISTRY_URL;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD;

/**
 * @author Ori Yafe
 */
@Getter
@Setter
public class PubTypeSpecificConfigModel extends TypeSpecificConfigModelBase {

    //virtual
    private Long virtualRetrievalCachePeriodSecs = DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD;

    //remote
    private String registryUrl = DEFAULT_PUB_REGISTRY_URL;

    @Override
    public void validateVirtualTypeSpecific(AddonsManager addonsManager) {
        setVirtualRetrievalCachePeriodSecs(ofNullable(getVirtualRetrievalCachePeriodSecs())
                .orElse(DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD));
    }

    @Override
    public void validateRemoteTypeSpecific() throws RepoConfigException {
        setRegistryUrl(ofNullable(getRegistryUrl()).orElse(DEFAULT_PUB_REGISTRY_URL));
    }

    @Override
    public RepoType getRepoType() {
        return RepoType.Pub;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
