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

package org.artifactory.lifecycle.converter.postinit.v102;

import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.lifecycle.converter.helpers.ConvertersManagerTestBase;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.test.TestUtils;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.artifactory.lifecycle.converter.postinit.v102.RemoteRepoBypassHeadSystemPropsConverter.PROP_KEY;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Dan Feldman
 */
@Test
public class RemoteRepoBypassHeadSystemPropsConverterTest extends ConvertersManagerTestBase {

    private static final String REMOTE1 = "danf-remote";
    private static final String REMOTE2 = "ChilliAndRay-remote";
    private static final String NOT_IN_LIST_REMOTE = "not-in-list-remote";

    //This is how its defined in PostInitVersion
    private RemoteRepoBypassHeadSystemPropsConverter converter = new RemoteRepoBypassHeadSystemPropsConverter(
            ArtifactoryServiceVersionProvider.v5412.get(), ArtifactoryServiceVersionProvider.v640m007.get());
    private CompoundVersionDetails goodFrom =
            new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v5412.get(), null, 0L);
    private CompoundVersionDetails goodUntil =
            new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v640m007.get(),
                    null, 0L);

    @BeforeClass
    public void setup() {
        System.clearProperty(PROP_KEY);
    }

    @AfterClass(alwaysRun = true)
    public void clear() {
        System.clearProperty(PROP_KEY);
        ArtifactoryHomeStub.unbind();
    }

    public void testInvalidVersion() {
        CompoundVersionDetails badFrom =
                new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v640m007.get(), null, 0L);
        CompoundVersionDetails badUntil =
                new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v552.get(), null, 0L);

        assertFalse(converter.isInterested(badFrom, badUntil));
        assertFalse(converter.isInterested(goodFrom, badUntil));
        assertFalse(converter.isInterested(badFrom, goodUntil));
    }

    public void testValidVersionValidRepoList() throws IOException {
        System.setProperty(PROP_KEY, "my-repo, your-repo");
        initHome();
        assertTrue(converter.isInterested(goodFrom, goodUntil));
    }

    public void testValidVersionInvalidRepoList() throws IOException {
        System.clearProperty(PROP_KEY);
        initHome();
        assertFalse(converter.isInterested(goodFrom, goodUntil));
    }

    public void testConversion() throws IOException {
        System.setProperty(PROP_KEY, REMOTE1 + "," + REMOTE2);
        initHome();
        Map<String, RemoteRepoConfig> remoteRepoConfigs = new HashMap<>();
        remoteRepoConfigs.put(REMOTE1, RemoteRepoConfig.create(REMOTE1, RepoType.Generic));
        remoteRepoConfigs.put(REMOTE2, RemoteRepoConfig.create(REMOTE2, RepoType.Generic));
        remoteRepoConfigs.put(NOT_IN_LIST_REMOTE, RemoteRepoConfig.create(NOT_IN_LIST_REMOTE, RepoType.Generic));

        assertFalse(remoteRepoConfigs.get(REMOTE1).getRepoTypeConfig().isBypassHeadRequests());
        assertFalse(remoteRepoConfigs.get(REMOTE2).getRepoTypeConfig().isBypassHeadRequests());
        assertFalse(remoteRepoConfigs.get(NOT_IN_LIST_REMOTE).getRepoTypeConfig().isBypassHeadRequests());

        Map<String, RemoteRepoConfig> remoteRepoToUpdate =
                converter.addRepoConfigWhereNeeded(remoteRepoConfigs.values().stream().toList()).stream()
                        .collect(Collectors.toMap(RepoConfig::getKey, Function.identity()));

        assertTrue(remoteRepoToUpdate.containsKey(REMOTE1));
        assertTrue(remoteRepoToUpdate.get(REMOTE1).getRepoTypeConfig().isBypassHeadRequests());
        assertTrue(remoteRepoToUpdate.containsKey(REMOTE2));
        assertTrue(remoteRepoToUpdate.get(REMOTE2).getRepoTypeConfig().isBypassHeadRequests());
        assertFalse(remoteRepoToUpdate.containsKey(NOT_IN_LIST_REMOTE));
    }

    private void initHome() throws IOException {
        File home = TestUtils.createTempDir(getClass());
        //Doesn't really matter
        createHomeEnvironment(home, ArtifactoryServiceVersionProvider.v5412.get());
        ArtifactoryHomeStub artifactoryHomeStub = new ArtifactoryHomeStub(home.getAbsolutePath());
        ArtifactoryHomeStub.bind(artifactoryHomeStub);
        artifactoryHomeStub.initArtifactorySystemProperties();
    }

    private RemoteRepoDescriptor getRemote(MutableCentralConfigDescriptor descriptor, String repoKey) {
        return descriptor.getRemoteRepositoriesMap().get(repoKey);
    }


}