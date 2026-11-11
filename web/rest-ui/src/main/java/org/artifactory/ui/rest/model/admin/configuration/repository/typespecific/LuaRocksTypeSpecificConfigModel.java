package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.util.JsonUtil;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_LUAROCKS_REGISTRY_URL;

public class LuaRocksTypeSpecificConfigModel extends TypeSpecificConfigModelBase {

    @Getter
    @Setter
    private String url = DEFAULT_LUAROCKS_REGISTRY_URL;

    @Override
    public RepoType getRepoType() {
        return RepoType.LuaRocks;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
