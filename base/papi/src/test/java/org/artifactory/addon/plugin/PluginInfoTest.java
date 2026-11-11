/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.addon.plugin;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Noam Y. Tenne
 */
class PluginInfoTest {

    @Test
    void testConstructorWithNoClosureMap() {
        PluginInfo info = new PluginInfo("name", null);
        assertEquals("name", info.getName());
        assertNull(info.getDescription());
        assertTrue(info.getParams().isEmpty());
        assertEquals("undefined", info.getVersion());
        assertFalse(info.isGroupPermitted("any"));
        assertFalse(info.isUserPermitted("any"));
    }

    @Test
    void testConstructor() {
        Map<String, Object> paramMap = Maps.newHashMap();
        paramMap.put("version", "1.0");
        paramMap.put("description", "desc");

        List<String> userList = Lists.newArrayList("user1", "user2");
        paramMap.put("users", userList);

        List<String> groupList = Lists.newArrayList("group1", "group2");
        paramMap.put("groups", groupList);

        Map<Object, Object> infoParams = Maps.newHashMap();
        paramMap.put("params", infoParams);
        PluginInfo info = new PluginInfo("name", paramMap);

        assertEquals("name", info.getName());
        assertEquals("1.0", info.getVersion());
        assertEquals("desc", info.getDescription());
        assertEquals(info.getParams(), infoParams);
        assertTrue(info.isGroupPermitted("group1"));
        assertTrue(info.isGroupPermitted("group2"));
        assertFalse(info.isGroupPermitted("group3"));
        assertTrue(info.isUserPermitted("user1"));
        assertTrue(info.isUserPermitted("user2"));
        assertFalse(info.isUserPermitted("user3"));
    }
}
