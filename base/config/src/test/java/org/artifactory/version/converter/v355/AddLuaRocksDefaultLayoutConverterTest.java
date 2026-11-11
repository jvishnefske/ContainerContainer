/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2026 JFrog Ltd.
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

package org.artifactory.version.converter.v355;

import org.artifactory.util.RepoLayoutUtils;
import org.artifactory.version.ArtifactoryConfigVersion;
import org.artifactory.version.converter.XmlConverter;
import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Muskan Gautam
 */
public class AddLuaRocksDefaultLayoutConverterTest extends AddonsLayoutConverterTestBase {

    private static final String FIXTURE = "/config/test/config.3.5.3_without_luarocks_layout.xml";

    @Test
    public void testConvertFromPreviousConfigVersion() throws Exception {
        Document document = convertXml(FIXTURE, new AddLuaRocksDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        List<Element> repoLayoutElements = repoLayoutsElement.getChildren();

        checkLayout(repoLayoutElements, namespace, RepoLayoutUtils.LUAROCKS_DEFAULT_NAME,
                "[module]/[module]-[baseRev](-[fileItegRev]).[ext]",
                "false", null, ".*", ".*");
    }

    @Test
    public void testConverterIsIdempotent() throws Exception {
        Document document = convertXml(FIXTURE, new AddLuaRocksDefaultLayoutConverter());

        int countAfterFirst = countLuaRocksLayouts(document);
        assertEquals(countAfterFirst, 1, "Should have exactly one LuaRocks layout after first conversion");

        new AddLuaRocksDefaultLayoutConverter().convert(document);

        int countAfterSecond = countLuaRocksLayouts(document);
        assertEquals(countAfterSecond, 1,
                "Should still have exactly one LuaRocks layout after second conversion (idempotent)");
    }

    @Test
    public void testLuaRocksRepoLayoutMigrated() throws Exception {
        Document document = convertXml(FIXTURE, new AddLuaRocksDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepos = rootElement.getChild("localRepositories", namespace);
        List<Element> repos = localRepos.getChildren("localRepository", namespace);
        Element luaRocksRepo =
                repos.stream().filter(r -> "luarocks-local".equals(r.getChildText("key", namespace))).findFirst()
                        .orElseThrow(() -> new AssertionError("luarocks-local repo not found"));
        assertEquals(luaRocksRepo.getChildText("repoLayoutRef", namespace),
                RepoLayoutUtils.LUAROCKS_DEFAULT_NAME,
                "luarocks-local repoLayoutRef should be migrated from simple-default to luarocks-default");
    }

    @Test
    public void testFederatedLuaRocksRepoLayoutMigrated() throws Exception {
        Document document = convertXml(FIXTURE, new AddLuaRocksDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element federatedRepos = rootElement.getChild("federatedRepositories", namespace);
        Element luaRocksRepo = federatedRepos.getChildren("federatedRepository", namespace).stream()
                .filter(r -> "luarocks-federated".equals(r.getChildText("key", namespace))).findFirst()
                .orElseThrow(() -> new AssertionError("luarocks-federated repo not found"));
        assertEquals(luaRocksRepo.getChildText("repoLayoutRef", namespace),
                RepoLayoutUtils.LUAROCKS_DEFAULT_NAME,
                "luarocks-federated repoLayoutRef should be migrated from simple-default to luarocks-default");
    }

    @Test
    public void testLuaRocksRepoCustomLayoutMigrated() throws Exception {
        Document document = convertXml(FIXTURE, new AddLuaRocksDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepos = rootElement.getChild("localRepositories", namespace);
        Element luaRocksRepo = localRepos.getChildren("localRepository", namespace).stream()
                .filter(r -> "luarocks-local-custom".equals(r.getChildText("key", namespace))).findFirst()
                .orElseThrow(() -> new AssertionError("luarocks-local-custom repo not found"));
        assertEquals(luaRocksRepo.getChildText("repoLayoutRef", namespace),
                RepoLayoutUtils.LUAROCKS_DEFAULT_NAME,
                "luarocks-local-custom repoLayoutRef should be migrated"
                        + " from maven-2-default to luarocks-default");
    }

    @Test
    public void testNonLuaRocksRepoLayoutNotChanged() throws Exception {
        Document document = convertXml(FIXTURE, new AddLuaRocksDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepos = rootElement.getChild("localRepositories", namespace);
        Element genericRepo = localRepos.getChildren("localRepository", namespace).stream()
                .filter(r -> "generic-local".equals(r.getChildText("key", namespace))).findFirst()
                .orElseThrow(() -> new AssertionError("generic-local repo not found"));
        assertEquals(genericRepo.getChildText("repoLayoutRef", namespace), "simple-default",
                "Non-luarocks repo layout should not be changed");
    }

    @Test
    public void testConverterRegisteredForVersion() {
        XmlConverter[] converters = ArtifactoryConfigVersion.v354.getConverters();
        boolean hasLuaRocksConverter =
                Arrays.stream(converters).anyMatch(c -> c instanceof AddLuaRocksDefaultLayoutConverter);
        assertTrue(hasLuaRocksConverter, "AddLuaRocksDefaultLayoutConverter should be registered for v354");
    }

    private int countLuaRocksLayouts(Document document) {
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);

        return (int) repoLayoutsElement.getChildren().stream()
                .filter(element -> element.getChildren().stream().filter(e -> "name".equals(e.getName())).anyMatch(
                        e -> RepoLayoutUtils.LUAROCKS_DEFAULT_NAME.equals(e.getContent().getFirst().getValue())))
                .count();
    }
}
