package org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.springframework.stereotype.Component;

/**
 * @author arkojd
 */
@Component
public class CargoRemoteRepoValidator implements RemoteRepoPackageSpecificValidator {

    private static final String CARGO_BASE_URL_ERR_MSG =
            "Custom Base URL should be defined prior to creating a Cargo repository";

    @Override
    public void validate(RemoteRepoConfig remoteRepoConfig, MutableCentralConfigDescriptor configDescriptor,
            RemoteRepositoryConfigModel model) throws RepoConfigException {
        if (RepoType.Cargo == remoteRepoConfig.getPackageType() &&
                StringUtils.isBlank(configDescriptor.getUrlBase())) {
            throw new RepoConfigException(CARGO_BASE_URL_ERR_MSG, HttpStatus.SC_BAD_REQUEST);
        }
    }


    @Override
    public RepoType getRepoType() {
        return RepoType.Cargo;
    }
}
