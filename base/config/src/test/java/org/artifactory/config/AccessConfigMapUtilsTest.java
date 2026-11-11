package org.artifactory.config;

import lombok.extern.slf4j.Slf4j;
import org.jfrog.common.JsonUtils;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.testng.collections.Maps;

import java.util.Map;

/**
 * @author Mor Merhav
 */
@Slf4j
class AccessConfigMapUtilsTest {

    @Test
    void addUserPlugin() throws JSONException {
        Map<String, Object> accessConfigMap = Maps.newHashMap();
        AccessConfigMapUtils.addUserPlugin(true, accessConfigMap);
        String accessConfigMapJson = JsonUtils.getInstance().writeValueAsString(accessConfigMap);
        JSONAssert.assertEquals("{\"security\":{\"authentication\":{\"user-plugin\":true}}}", accessConfigMapJson,
                JSONCompareMode.STRICT);

        AccessConfigMapUtils.addUserPlugin(false, accessConfigMap);
        accessConfigMapJson = JsonUtils.getInstance().writeValueAsString(accessConfigMap);
        JSONAssert.assertEquals("{\"security\":{\"authentication\":{\"user-plugin\":false}}}", accessConfigMapJson,
                JSONCompareMode.STRICT);
    }
}