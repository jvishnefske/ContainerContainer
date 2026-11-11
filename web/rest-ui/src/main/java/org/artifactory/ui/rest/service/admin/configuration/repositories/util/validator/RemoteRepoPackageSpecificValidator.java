package org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator;

import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;

/**
 * @author arkojd
 */
public interface RemoteRepoPackageSpecificValidator {
    void validate(RemoteRepoConfig remoteRepoConfig, MutableCentralConfigDescriptor configDescriptor,
            RemoteRepositoryConfigModel model) throws RepoConfigException;

    RepoType getRepoType();
}
