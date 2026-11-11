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

package org.artifactory.descriptor.repo;

import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.artifactory.repo.xray.XrayNotAvailableMode.USE_SYSTEM_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * Tests the RemoteRepoDescriptor class.
 *
 * @author Yossi Shaul
 */


class RemoteRepoDescriptorTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void defaultConstructor() {
        RemoteRepoDescriptor remote = new RemoteRepoDescriptor() {
        };
        assertNull(remote.getKey());
        assertEquals(remote.getIncludesPattern(), "**/*");
        assertNull(remote.getExcludesPattern());
        assertNull(remote.getDescription());
        assertNull(remote.getRepoLayout());
        assertEquals(remote.getAssumedOfflinePeriodSecs(), 300);
        assertEquals(remote.getMaxUniqueSnapshots(), 0);
        assertEquals(remote.getMaxUniqueTags(), 0);
        assertEquals(remote.getMissedRetrievalCachePeriodSecs(), 1800);
        assertEquals(remote.getRetrievalCachePeriodSecs(), 7200);
        assertEquals(remote.getMetadataRetrievalTimeoutSecs(), 60);
        assertEquals(remote.getChecksumPolicyType(), ChecksumPolicyTypeDescriptor.GEN_IF_ABSENT);
        assertNull(remote.getUrl());
        assertNull(remote.getRemoteRepoLayout());
        assertFalse(remote.isOffline());
        assertFalse(remote.isBlackedOut());
        assertFalse(remote.isCache());
        assertFalse(remote.isLocal());
        assertFalse(remote.isHardFail());
        assertTrue(remote.isStoreArtifactsLocally());
        assertFalse(remote.isFetchJarsEagerly());
        assertFalse(remote.isFetchSourcesEagerly());
        assertTrue(remote.isSuppressPomConsistencyChecks(),
                "Default should not supress pom consistency checks");
        assertEquals(remote.getUnusedArtifactsCleanupPeriodHours(), 0);
        assertFalse(remote.isShareConfiguration());
        assertNotNull(remote.getPropertySets(), "Property sets list should not be null");
        assertFalse(remote.isRejectInvalidJars());
        assertNotEquals(remote.getType(), RepoType.NuGet, "NuGet should be off by default.");
        assertEquals(remote.getDockerApiVersion(), DockerApiVersion.V2, "Docker API version should be V2 by default.");
        assertNull(remote.getHexRepositoryUrl());
        assertNull(remote.getHexPublicKey());
        assertFalse(remote.isListRemoteFolderItems());
        assertFalse(remote.isMlRepoLayout());
        assertFalse(remote.isGitLabResolveSubgroups());
    }

    @Test
    void testMetadataTimeoutSetterGetter() {
        RemoteRepoDescriptor remote = new RemoteRepoDescriptor() {
        };
        remote.setMetadataRetrievalTimeoutSecs(10);
        assertEquals(remote.getMetadataRetrievalTimeoutSecs(), 10);
    }

    @Test
    void testExternalDependencies() {
        RemoteRepoDescriptor remote = new RemoteRepoDescriptor() {
        };
        ExternalDependenciesConfig externalDependenciesConfig = remote.getExternalDependencies();
        assertNull(externalDependenciesConfig);

        ExternalDependenciesConfig externalDependencies = new ExternalDependenciesConfig();
        externalDependencies.setEnabled(true);
        externalDependencies.setPatterns(List.of("a", "b"));
        remote.setExternalDependencies(externalDependencies);
        assertTrue(remote.isExternalDependenciesConfigEnabled());
        assertEquals(remote.getExternalDependenciesConfigPatterns(), "a,b");
    }

    @Test
    void shouldCallEmptyProcessUrlForNonHttpRepoDescriptor() {
        final RemoteRepoDescriptor remoteRepoDescriptor = Mockito.spy(RemoteRepoDescriptor.class);
        remoteRepoDescriptor.processUrl();
        Mockito.verify(remoteRepoDescriptor, Mockito.times(0)).setUrl(any());
    }

    @Test
    void shouldProcessUrlForHttpRepoDescriptor() {
        final RemoteRepoDescriptor remoteRepoDescriptor = Mockito.spy(HttpRepoDescriptor.class);
        when(remoteRepoDescriptor.getUrl()).thenReturn("https://admin:password@localhost:8080/conan-smart-remote");
        remoteRepoDescriptor.processUrl();
        Mockito.verify(remoteRepoDescriptor).processUrl();
        Mockito.verify(remoteRepoDescriptor, Mockito.times(1)).setUrl(any());
    }

    @Test
    void testMlRepoLayoutNonDefaultValue() {
        final RemoteRepoDescriptor remoteRepoDescriptor = Mockito.spy(HttpRepoDescriptor.class);
        HuggingFaceMLRemoteConfiguration huggingFaceMLConfiguration = new HuggingFaceMLRemoteConfiguration();
        huggingFaceMLConfiguration.setMlRepoLayout(true);
        setField(remoteRepoDescriptor, "huggingFace", huggingFaceMLConfiguration);
        assertTrue(remoteRepoDescriptor.isMlRepoLayout());
    }

    @Test
    void testGoRepoLayoutNonDefaultValue() {
        final RemoteRepoDescriptor remoteRepoDescriptor = Mockito.spy(HttpRepoDescriptor.class);
        GoConfiguration goRemoteConfiguration = new GoConfiguration();
        goRemoteConfiguration.setGitLabResolveSubgroups(true);
        setField(remoteRepoDescriptor, "go", goRemoteConfiguration);
        assertTrue(remoteRepoDescriptor.isGitLabResolveSubgroups());
    }

    @Test
    void testXrayNotAvailableMode() {
        final RemoteRepoDescriptor remoteRepoDescriptor = Mockito.spy(HttpRepoDescriptor.class);
        assertEquals(USE_SYSTEM_PROPERTY, remoteRepoDescriptor.getXrayNotAvailableMode());
        remoteRepoDescriptor.setXrayNotAvailableMode(XrayNotAvailableMode.FAIL_CLOSE);
        assertEquals(XrayNotAvailableMode.FAIL_CLOSE, remoteRepoDescriptor.getXrayNotAvailableMode());
    }
}
