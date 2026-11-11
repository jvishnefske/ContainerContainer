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

package org.artifactory.storage.db.aql.itest.service;

import org.artifactory.aql.result.AqlRestResult;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.artifactory.storage.db.aql.itest.service.AqlJsonHelper.compareJsons;
import static org.artifactory.storage.db.aql.itest.service.AqlJsonHelper.load;
import static org.artifactory.storage.db.aql.itest.service.AqlJsonHelper.readCompact;

/**
 * @author Gidi Shabat
 */
@Test
public class AqlJSonResultTest extends AqlAbstractServiceTest {

    @DataProvider(name = "queries")
    public Object[][] data() {
        return new Object[][] {
                {
                        //findBuildWithItemsAndItemProperties
                        "builds.find().include(\"module.dependency.item.*\",\"module.dependency.item.property.*\")",
                        "/aql/stream/buildsWithItems.json",
                        true
                },
                {
                        //findBuildWithArtifacts
                        "builds.find().include(\"module.dependency.*\")",
                        "/aql/stream/buildsWithArtifacts.json",
                        true
                },
                {
                        //findBuildWithDependencies
                        "builds.find().include(\"module.dependency.*\")",
                        "/aql/stream/buildsWithDependency.json",
                        true
                },
                {
                        //findBuildWithModules
                        "builds.find().include(\"module.*\")",
                        "/aql/stream/buildsWithModules.json",
                        true
                },
                {
                        //itemsWithProperties
                        "items.find().include(\"property.*\")",
                        "/aql/stream/itemsWithProperties.json",
                        false
                },
                {
                        //buildWithProperties
                        "builds.find().include(\"property.*\")",
                        "/aql/stream/buildsWithProperties.json",
                        true
                },
                {
                        //modulesWithProperties
                        "modules.find().include(\"property.*\")",
                        "/aql/stream/modulesWithProperties.json",
                        true
                },
                {
                        //dependenciesWithStats
                        "dependencies.find().include(\"item.stat.*\")",
                        "/aql/stream/dependenciesWithStats.json",
                        true
                },
                {
                        //items
                        "items.find()",
                        "/aql/stream/items.json",
                        false
                },
                {
                        //itemsWithReleaseBundles
                        "items.find().include(\"release_artifact.release\")",
                        "/aql/stream/itemsWithReleaseBundles.json",
                        false
                },
                {
                        //itemsInReleaseBundle
                        "items.find({\"release_artifact.release.version\":\"1.0.1\", \"release_artifact.release" +
                                ".name\" : " +
                                "\"bundle2\"}).include(\"release_artifact.release\")",
                        "/aql/stream/itemsInReleaseBundle.json",
                        false
                },
                {
                        //itemsInReleaseBundleByDate
                        "items.find({\"release_artifact.release.version\":\"1.0.0\", \"release_artifact.release" +
                                ".created\" : " +
                                "{\"$before\" : \"2w\"}}).include(\"release_artifact.path\")",
                        "/aql/stream/itemsInReleaseBundleByDate.json",
                        false
                },
                {
                        //releaseBundleItems
                        "releases.find({\"type\": \"TARGET\", \"release_artifact.item" +
                                ".sha256\":\"dcabf055bc6d5477c35f82da16323efb884fc21a87fbf7ebda9d5848eee3e280\"})" +
                                ".include" +
                                "(\"release_artifact.item\")",
                        "/aql/stream/releaseBundleItems.json",
                        true
                },
                {
                        //propertiesWithItems
                        "properties.find().include(\"item.*\")",
                        "/aql/stream/propertiesWithItems.json",
                        true
                },
        };
    }

    @Test(dataProvider = "queries")
    public void test(String query, String expected, boolean isAdmin) throws IOException {
        // Some tests require admin permissions, some don't
        AdminPermissions.isAdmin = isAdmin;

        // TODO: RTDEV-28417 - the "correct" result should not contain _intransit since it should be filtered.
        try (AqlRestResult aqlStreamer = aqlService.executeQueryLazy(query)) {
            String result = readCompact(aqlStreamer);
            String expectation = load(expected);
            compareJsons(result, expectation);
        }

        try (AqlRestResult aqlStreamer = aqlService.executeQueryLazy(query)) {
            String result = read(aqlStreamer);
            String expectation = load(expected);
            compareJsons(result, expectation);
        }

        // Restore the default admin permissions
        AdminPermissions.isAdmin = true;
    }

    @Test(enabled = false)
    public void releaseBundleInBuilds() throws IOException {
        try (AqlRestResult aqlStreamer = aqlService.executeQueryLazy(
                "releases.find({\"release_artifact.item.dependency.module.build.name\": \"bb\", \"release_artifact" +
                        ".item.dependency.module.build.number\": 1}).include(\"release_artifact.item.dependency" +
                        ".module.build\")")) {
            String result = read(aqlStreamer);
            String expectation = load("/aql/stream/releaseBundlesInBuilds.json");
            compareJsons(result, expectation);
        }
    }

    @Test(enabled = false)
    public void buildsWithReleaseBundleItems() throws IOException {
        try (AqlRestResult aqlStreamer = aqlService.executeQueryLazy(
                "builds.find({\"module.dependency.item.release_artifact.release.name\":\"bundle1\"}).include(\"module" +
                        ".dependency.item.release_artifact\")")) {
            String result = read(aqlStreamer);
            String expectation = load("/aql/stream/buildsWithReleaseArtifacts.json");
            compareJsons(result, expectation);
        }
    }

    @Test(enabled = false)
    public void buildsWithReleaseBundles() throws IOException {
        try (AqlRestResult aqlStreamer = aqlService.executeQueryLazy(
                "builds.find({\"module.dependency.item.release_artifact.release.name\":\"bundle2\"}).include(\"module" +
                        ".dependency.item.release_artifact.release.name\", \"module.dependency.item.release_artifact" +
                        ".release.signature\")")) {
            String result = read(aqlStreamer);
            String expectation = load("/aql/stream/buildsWithReleaseBundles.json");
            compareJsons(result, expectation);
        }
    }

    private String read(AqlRestResult aqlStreamer) {
        StringBuilder builder = new StringBuilder();
        byte[] tempResult;
        while ((tempResult = aqlStreamer.read()) != null) {
            builder.append(new String(tempResult));
        }
        return builder.toString();
    }
}


