package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.addon.AddonsManager;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.util.JsonUtil;

import static java.util.Optional.ofNullable;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_CARGO_REGISTRY;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD;

@Getter
@Setter
public class CargoTypeSpecificConfigModel extends TypeSpecificConfigModelBase {
    //virtual
    private Long virtualRetrievalCachePeriodSecs = DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD;

    //remote
    private String registryUrl = DEFAULT_CARGO_REGISTRY;
    private boolean anonymousAccess; // remote and local
    private boolean internalIndex = true;

    @Override
    public void validateVirtualTypeSpecific(AddonsManager addonsManager) {
        setVirtualRetrievalCachePeriodSecs(ofNullable(getVirtualRetrievalCachePeriodSecs())
                .orElse(DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD));
    }

    @Override
    public void validateRemoteTypeSpecific() throws RepoConfigException {
        setRegistryUrl(ofNullable(getRegistryUrl()).orElse(DEFAULT_CARGO_REGISTRY));
    }

    @Override
    public RepoType getRepoType() {
        return RepoType.Cargo;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
