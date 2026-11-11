package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.rest.common.exception.RepoConfigException;

import static java.util.Optional.ofNullable;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_ENABLE_TOKEN_AUTHENTICATION;

/**
 * @author Igor Usenko
 */
public abstract class TypeSpecificConfigModelBase implements TypeSpecificConfigModel {

    //remote
    private Boolean enableTokenAuthentication = DEFAULT_ENABLE_TOKEN_AUTHENTICATION;

    @Override
    public Boolean isEnableTokenAuthentication() {
        return enableTokenAuthentication;
    }

    @Override
    public void setEnableTokenAuthentication(Boolean enableTokenAuthentication) {
        this.enableTokenAuthentication = enableTokenAuthentication;
    }

    @Override
    public void validateRemoteTypeSpecific() throws RepoConfigException {
        setEnableTokenAuthentication(ofNullable(isEnableTokenAuthentication())
                .orElse(DEFAULT_ENABLE_TOKEN_AUTHENTICATION));
    }
}
