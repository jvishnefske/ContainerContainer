package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.addon.AddonsManager;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.util.JsonUtil;

public class MLTypeSpecificConfigModel extends TypeSpecificConfigModelBase {

    @Override
    public void validateRemoteTypeSpecific() throws RepoConfigException {
        throwUnsupportedRemoteRepoType();
    }

    @Override
    public void validateVirtualTypeSpecific(AddonsManager addonsManager) throws RepoConfigException {
        throwUnsupportedVirtualRepoType();
    }

    @Override
    public RepoType getRepoType() {
        return RepoType.MachineLearning;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
