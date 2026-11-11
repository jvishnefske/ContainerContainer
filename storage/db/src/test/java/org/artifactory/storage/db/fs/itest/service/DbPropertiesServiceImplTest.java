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

package org.artifactory.storage.db.fs.itest.service;

import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import org.artifactory.md.Properties;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.fs.dao.PropertiesDao;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.test.TestUtils;
import org.jfrog.rtfs.dto.properties.NodePath;
import org.jfrog.rtfs.dto.properties.NodePropertyData;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.test.TestUtils.arrayOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
/**
 * @author Yossi Shaul
 */
@Test
public class DbPropertiesServiceImplTest extends DbBaseTest {

    @Autowired
    private PropertiesService propertiesService;

    @Autowired
    private PropertiesDao propsDao;

    @BeforeClass
    public void setup() {
        importSql("/sql/nodes-for-service.sql");
    }

    public void getPropertiesNodeWithProperties() {
        Properties props = loadProperties(5);
        assertEquals(props.keySet().size(), 2);
        String value = props.getFirst("build.name");
        assertEquals(value, "ant");
    }

    public void getPropertiesByRepoPath() {
        Properties props = propertiesService.getProperties(new RepoPathImpl("repo1", "ant/ant/1.5/ant-1.5.jar"));
        assertEquals(props.keySet().size(), 2);
        String value = props.getFirst("build.name");
        assertEquals(value, "ant");
    }

    public void getPropertiesItemInfoWithId() {
        Properties props = propertiesService.getProperties(
                new FileInfoImpl(new RepoPathImpl("repo1", "ant/ant/1.5/ant-1.5.jar"), 5));
        assertEquals(props.keySet().size(), 2);
        String value = props.getFirst("build.name");
        assertEquals(value, "ant");
    }

    public void getPropertiesItemInfoWithoutId() {
        Properties props = propertiesService.getProperties(
                new FileInfoImpl(new RepoPathImpl("repo1", "ant/ant/1.5/ant-1.5.jar")));
        assertEquals(props.keySet().size(), 2);
        String value = props.getFirst("build.name");
        assertEquals(value, "ant");
    }

    public void getPropertiesNodeWithMultiValueProperties() {
        Properties props = loadProperties(7);

        assertEquals(props.keySet().size(), 1, "One unique key");
        Set<String> values = props.get("yossis");
        assertNotNull(values);
        assertTrue(values.contains("value1"));
        assertTrue(values.contains("value2"));
    }


    public void getPropertiesNodeWithNone() {
        assertEquals(loadProperties(2).size(), 0);
    }

    public void getPropertiesNodeNotExist() {
        assertEquals(loadProperties(78849).size(), 0);
    }

    public void overrideProperties() {
        assertEquals(loadProperties(6).size(), 0);
        PropertiesImpl properties = new PropertiesImpl();
        properties.put("key1", "1");
        properties.put("key1", "2");
        properties.put("key2", "2");

        boolean modified =
                propertiesService.setProperties(RepoPathFactory.create("repo1", "org"), properties);
        assertEquals(loadProperties(6), properties);
        assertTrue(modified, "Method should have reported on modification");
    }

    @Test(dependsOnMethods = "overrideProperties")
    public void deleteProperties() {
        int count = propertiesService.deleteProperties(6);
        assertEquals(count, 3);

        assertEquals(loadProperties(6).size(), 0);
    }

    public void hasPropertiesPathWithProperties() {
        assertTrue(propertiesService.hasProperties(new RepoPathImpl("repo1", "ant/ant/1.5/ant-1.5.jar")));
    }

    public void hasPropertiesPathWithNoProperties() {
        assertFalse(propertiesService.hasProperties(new RepoPathImpl("repo2", "org")));
    }

    public void testUpdateProperties() throws Exception {
        int nodeId = 602;
        propertiesService.deleteProperties(nodeId);
        Properties properties = loadProperties(nodeId);
        assertTrue(properties.isEmpty());
        properties.put("key1", "value1a");
        properties.put("key1", "value1b");
        properties.put("key2", "value2");
        assertTrue(propertiesService.setProperties(RepoPathFactory.create("repo-copy", "org/shayy/badmd5/badmd5.jar"),
                properties));

        Properties savedProperties = loadProperties(nodeId);
        assertEquals(savedProperties.keySet().size(), 2);
        assertEquals(savedProperties.size(), 3);
        assertEquals(savedProperties.get("key1"), Sets.newHashSet("value1a", "value1b"));
        assertEquals(savedProperties.get("key2"), Sets.newHashSet("value2"));
        Map<String, Set<Long>> key2propId = getPropKey2PropIdsMap(nodeId);

        properties.replaceValues("key2", Arrays.asList("value2a", "value2b"));
        properties.put("key3", "value3");
        assertTrue(propertiesService.setProperties(RepoPathFactory.create("repo-copy", "org/shayy/badmd5/badmd5.jar"),
                properties));

        savedProperties = loadProperties(nodeId);
        assertEquals(savedProperties.keySet().size(), 3);
        assertEquals(savedProperties.size(), 5);
        assertEquals(savedProperties.get("key1"), Sets.newHashSet("value1a", "value1b"));
        assertEquals(savedProperties.get("key2"), Sets.newHashSet("value2a", "value2b"));
        assertEquals(savedProperties.get("key3"), Sets.newHashSet("value3"));
        Map<String, Set<Long>> newKey2propId = getPropKey2PropIdsMap(nodeId);
        assertTrue(newKey2propId.get("key1").containsAll(key2propId.get("key1")));
        assertTrue(newKey2propId.get("key2").containsAll(key2propId.get("key2")));

        properties = loadProperties(nodeId);
        properties.removeAll("key1");
        properties.removeAll("key2");
        assertTrue(propertiesService.setProperties(RepoPathFactory.create("repo-copy", "org/shayy/badmd5/badmd5.jar"),
                properties));

        savedProperties = loadProperties(nodeId);
        assertEquals(savedProperties.keySet().size(), 1);
        assertEquals(savedProperties.size(), 1);
        assertEquals(savedProperties.get("key3"), Sets.newHashSet("value3"));
    }

    public void testSetPropertiesWithChangeTrackingUsedOnlyForTheOwningNode() throws Exception {
        int nodeId1 = 601;
        int nodeId2 = 602;
        propertiesService.deleteProperties(nodeId1);
        propertiesService.deleteProperties(nodeId2);
        Properties properties1 = loadProperties(nodeId1);
        Properties properties2 = loadProperties(nodeId2);
        assertTrue(properties1.isEmpty());
        assertTrue(properties2.isEmpty());
        properties1.put("key1", "value1a");
        assertTrue(propertiesService.setProperties(RepoPathFactory.create("repo-copy", "org/shayy/badsha1/badsha1.jar"),
                properties1));
        properties2.put("key1", "value1a");
        assertTrue(propertiesService.setProperties(RepoPathFactory.create("repo-copy", "org/shayy/badmd5/badmd5.jar"),
                properties2));
        long propId1 = getPropKey2PropIdsMap(nodeId1).get("key1").iterator().next();
        long propId2 = getPropKey2PropIdsMap(nodeId2).get("key1").iterator().next();
        assertTrue(propId1 != propId2);

        properties1 = loadProperties(nodeId1);
        properties1.replaceValues("key1", Collections.singletonList("value1b"));
        assertTrue(propertiesService.setProperties(RepoPathFactory.create("repo-copy", "org/shayy/badsha1/badsha1.jar"),
                properties1));
        assertTrue(propertiesService.setProperties(RepoPathFactory.create("repo-copy", "org/shayy/badmd5/badmd5.jar"),
                properties1));
        long propId1b = getPropKey2PropIdsMap(nodeId1).get("key1").iterator().next();
        long propId2b = getPropKey2PropIdsMap(nodeId2).get("key1").iterator().next();
        assertTrue(propId1 == propId1b);
        assertFalse(propId2 == propId2b);
        properties1 = loadProperties(nodeId1);
        properties2 = loadProperties(nodeId2);
        assertEquals(properties1.get("key1"), Collections.singleton("value1b"));
        assertEquals(properties2.get("key1"), Collections.singleton("value1b"));
    }

    public void testGetStreamedNodePropertyDataOnePath() {
        Map<NodePath, SetMultimap<String, NodePropertyData>> repo1 =
                propertiesService.getStreamedNodePropertyData(List.of(new NodePath("ant/ant/1.5", "ant-1.5.jar")),
                        "repo1");
        assertEquals(repo1.size(), 1);
        Map<String, Collection<NodePropertyData>> map = repo1.get(new NodePath("ant/ant/1.5", "ant-1.5.jar")).asMap();
        assertEquals(map.size(), 2);
        assertTrue(map.containsKey("build.name") && map.get("build.name").size() == 1);
        assertTrue(map.get("build.name").contains(new NodePropertyData(100L, "ant")));
        assertTrue(map.containsKey("build.number") && map.get("build.number").size() == 1);
        assertTrue(map.get("build.number").contains(new NodePropertyData(101L, "67")));
    }

    public void testGetStreamedNodePropertyDataMultiPath() {
        Map<NodePath, SetMultimap<String, NodePropertyData>> repo1 =
                propertiesService.getStreamedNodePropertyData(
                        List.of(new NodePath("ant/ant/1.5", "ant-1.5.jar"), new NodePath("org", "yossis")),
                        "repo1");
        assertEquals(repo1.size(), 2);
        Map<String, Collection<NodePropertyData>> map = repo1.get(new NodePath("ant/ant/1.5", "ant-1.5.jar")).asMap();
        assertEquals(map.size(), 2);
        assertTrue(map.containsKey("build.name") && map.get("build.name").size() == 1);
        assertTrue(map.get("build.name").contains(new NodePropertyData(100L, "ant")));
        assertTrue(map.containsKey("build.number") && map.get("build.number").size() == 1);
        assertTrue(map.get("build.number").contains(new NodePropertyData(101L, "67")));
        Map<String, Collection<NodePropertyData>> map2 = repo1.get(new NodePath("org", "yossis")).asMap();
        assertEquals(map2.size(), 1);
        assertTrue(map2.containsKey("yossis") && map2.get("yossis").size() == 2);
        assertTrue(map2.get("yossis").contains(new NodePropertyData(102L, "value1")));
        assertTrue(map2.get("yossis").contains(new NodePropertyData(103L, "value2")));
    }

    public void testGetStreamedNodePropertyNoProps() {
        Map<NodePath, SetMultimap<String, NodePropertyData>> repo1 =
                propertiesService.getStreamedNodePropertyData(
                        List.of(new NodePath("a", "b")), "repo1");
        assertEquals(repo1.size(), 0);
    }

    private Map<String, Set<Long>> getPropKey2PropIdsMap(int nodeId) throws SQLException {
        Map<String, Set<Long>> key2propId = Maps.newHashMap();
        propsDao.getNodeProperties(nodeId).forEach(prop -> {
            Set<Long> ids = key2propId.computeIfAbsent(prop.getPropKey(), k -> Sets.newHashSet());
            ids.add(prop.getPropId());
        });
        return key2propId;
    }

    private Properties loadProperties(long nodeId) {
        return (Properties) TestUtils
                .invokeMethod(propertiesService, "loadProperties", arrayOf(Long.TYPE), arrayOf(nodeId));
    }
}
