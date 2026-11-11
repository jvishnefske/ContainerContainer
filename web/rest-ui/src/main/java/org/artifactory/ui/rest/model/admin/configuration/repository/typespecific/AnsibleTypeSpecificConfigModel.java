package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.util.JsonUtil;

import static java.util.Optional.ofNullable;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_ANSIBLE_REGISTRY_URL;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD;

@Getter
@Setter
public class AnsibleTypeSpecificConfigModel extends TypeSpecificConfigModelBase {

    private String url = DEFAULT_ANSIBLE_REGISTRY_URL;
    private boolean bypassHeadRequests = true;
    private Long virtualRetrievalCachePeriodSecs = DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD;
    private String authUrl = StringUtils.EMPTY;

    @Override
    public RepoType getRepoType() {
        return RepoType.Ansible;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }

    @Override
    public void validateRemoteTypeSpecific() throws RepoConfigException {
        setUrl(ofNullable(getUrl()).orElse(DEFAULT_ANSIBLE_REGISTRY_URL));
        setBypassHeadRequests(isBypassHeadRequests());
        setVirtualRetrievalCachePeriodSecs(
                ofNullable(getVirtualRetrievalCachePeriodSecs())
                        .orElse(DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD));
        super.validateRemoteTypeSpecific();
    }
}
