package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.descriptor.repo.RepoType;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_LUAROCKS_REGISTRY_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LuaRocksTypeSpecificConfigModelTest {

    @Test
    void getRepoType_shouldReturnLuaRocks_always() {
        LuaRocksTypeSpecificConfigModel luaRocksTypeSpecificConfigModel = new LuaRocksTypeSpecificConfigModel();
        assertEquals(RepoType.LuaRocks, luaRocksTypeSpecificConfigModel.getRepoType());
    }

    @Test
    void toString_shouldSerializeWithDefaultUrl_whenUsingDefaults() {
        LuaRocksTypeSpecificConfigModel luaRocksTypeSpecificConfigModel = new LuaRocksTypeSpecificConfigModel();
        LuaRocksTypeSpecificConfigModel luaRockType = JsonUtils.getInstance()
                .readValue(luaRocksTypeSpecificConfigModel.toString(), LuaRocksTypeSpecificConfigModel.class);
        assertEquals(DEFAULT_LUAROCKS_REGISTRY_URL, luaRockType.getUrl());
        assertEquals(RepoType.LuaRocks, luaRockType.getRepoType());
    }
}
