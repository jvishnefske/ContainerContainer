package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.util.JsonUtil;

import static java.util.Optional.ofNullable;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_HEX_REGISTRY_PUBLIC_KEY;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_HEX_REGISTRY_URL;

/**
 * @author Leela Padmaja
 */
@Getter
@Setter
public class HexTypeSpecificConfigModel extends TypeSpecificConfigModelBase {
    private String url = DEFAULT_HEX_REGISTRY_URL;
    private String hexPublicKey = DEFAULT_HEX_REGISTRY_PUBLIC_KEY;

    @Override
    public RepoType getRepoType() {
        return RepoType.Hex;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }

    @Override
    public void validateRemoteTypeSpecific() throws RepoConfigException {
        url = ofNullable(url).orElse(DEFAULT_HEX_REGISTRY_URL);
        hexPublicKey = ofNullable(hexPublicKey).orElse(DEFAULT_HEX_REGISTRY_PUBLIC_KEY);
    }
}
