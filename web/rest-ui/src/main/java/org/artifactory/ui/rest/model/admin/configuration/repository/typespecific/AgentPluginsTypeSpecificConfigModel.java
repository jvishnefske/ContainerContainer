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
public class AgentPluginsTypeSpecificConfigModel implements TypeSpecificConfigModel {

    @Override
    public RepoType getRepoType() {
        return RepoType.AgentPlugins;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
