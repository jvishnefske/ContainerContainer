package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.util.JsonUtil;

/**
 * @author Package Generator
 */
@Getter
@Setter
public class AgentPackagesTypeSpecificConfigModel implements TypeSpecificConfigModel {

    @Override
    public RepoType getRepoType() {
        return RepoType.AgentPackages;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
