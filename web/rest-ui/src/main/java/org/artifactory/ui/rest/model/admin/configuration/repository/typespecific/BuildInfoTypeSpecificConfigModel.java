package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.descriptor.repo.RepoType;

public class BuildInfoTypeSpecificConfigModel extends TypeSpecificConfigModelBase {
    @Override
    public RepoType getRepoType() {
        return RepoType.BuildInfo;
    }
}
