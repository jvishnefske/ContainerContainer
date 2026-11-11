package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.util.JsonUtil;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_BAZELMODULES_REGISTRY_URL;

/**
 * @author Volodymyr Shulga
 */
@Getter
@Setter
public class BazelModulesTypeSpecificConfigModel implements TypeSpecificConfigModel {
    private String url = DEFAULT_BAZELMODULES_REGISTRY_URL;

    @Override
    public RepoType getRepoType() {
        return RepoType.BazelModules;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
