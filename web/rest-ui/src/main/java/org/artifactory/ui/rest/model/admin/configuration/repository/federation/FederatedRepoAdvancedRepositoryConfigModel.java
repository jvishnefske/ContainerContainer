package org.artifactory.ui.rest.model.admin.configuration.repository.federation;

import org.artifactory.api.rest.restmodel.JsonUtil;
import org.artifactory.ui.rest.model.admin.configuration.repository.ProxyAwareConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalAdvancedRepositoryConfigModel;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_DISABLE_PROXY;

/**
 * @author Alexei Vainshtein
 */
public class FederatedRepoAdvancedRepositoryConfigModel extends LocalAdvancedRepositoryConfigModel
        implements ProxyAwareConfigModel {

    private String proxy;
    private Boolean disableProxy = DEFAULT_DISABLE_PROXY;

    @Override
    public String getProxy() {
        return proxy;
    }

    @Override
    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    @Override
    public Boolean isDisableProxy() {
        return disableProxy;
    }

    @Override
    public void setDisableProxy(Boolean disableProxy) {
        this.disableProxy = disableProxy;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
