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

import com.google.common.collect.Maps;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;
import org.testng.Assert;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.descriptor.repo.RandomObjectPopulator.createAndFill;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the VirtualRepoDescriptor.
 *
 * @author Yossi Shaul
 */
class VirtualRepoDescriptorTest extends ArtifactoryHomeBoundJUnitTest {

    private final LocalRepoDescriptor local1 = new LocalRepoDescriptor();
    private final LocalRepoDescriptor local2 = new LocalRepoDescriptor();

    @BeforeEach
     void initTestRepos() {
        local1.setKey("local1");
        local2.setKey("local2");
    }

    @Test
     void defaultConstructor() {
        VirtualRepoDescriptor virtualRepo = new VirtualRepoDescriptor();
        assertNull(virtualRepo.getKey());
        assertNull(virtualRepo.getRepoLayout());
        assertNull(virtualRepo.getKeyPair());
        assertTrue(virtualRepo.getRepositories().isEmpty());
        assertNotEquals(RepoType.NuGet, virtualRepo.getType(), "NuGet should be off by default.");
        assertEquals(DockerApiVersion.V2, virtualRepo.getDockerApiVersion(),
                "Docker API version should be V2 by default.");
    }

    @Test
    void testDescriptorToRepositoryConfigMapping() throws Exception {
        // given
        VirtualRepoDescriptor virtualRepoDescriptor = createAndFill(VirtualRepoDescriptor.class);
        VirtualRepoConfig repositoryConfig = virtualRepoDescriptor.toRepositoryConfig();
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        for (RepoType value : RepoType.values()) {
            virtualRepoDescriptor.buildPackageTypeConfig(value, packageTypeConfig);
        }

        Field packageTypeConfigField = ReflectionUtils.findField(VirtualRepoConfig.class, "packageTypeConfig");
        assertNotNull(packageTypeConfigField);
        ReflectionUtils.makeAccessible(packageTypeConfigField);
        ReflectionUtils.setField(packageTypeConfigField, repositoryConfig, packageTypeConfig);

        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setName(virtualRepoDescriptor.getRepoLayout().getName());
        KeyPairDescriptor primaryKeyPair = new KeyPairDescriptor();
        primaryKeyPair.setKey(virtualRepoDescriptor.getPrimaryKeyPairDescriptor().getKey());
        KeyPairDescriptor secondaryKeyPair = new KeyPairDescriptor();
        secondaryKeyPair.setKey(virtualRepoDescriptor.getSecondaryKeyPairDescriptor().getKey());

        // when
        LocalRepoDescriptor defaultDeploymentRepo = new LocalRepoDescriptor();
        defaultDeploymentRepo.setKey(virtualRepoDescriptor.getDefaultDeploymentRepo().getKey());
        Map<String, RepoDescriptor> realRepoDescriptors = Map.of(defaultDeploymentRepo.getKey(), defaultDeploymentRepo);
        VirtualRepoDescriptor mappedVirtualDescriptor =
                new VirtualRepoDescriptor(repositoryConfig, List.of(repoLayout), new HashMap<>(), Map.of(),
                        realRepoDescriptors,
                        List.of(primaryKeyPair, secondaryKeyPair), List.of(), List.of());
        for (RepoType value : RepoType.values()) {
            mappedVirtualDescriptor.setPackageTypeConfig(repositoryConfig.getPackageTypeConfig(), value,
                    realRepoDescriptors);
        }

        // then
        @SuppressWarnings("unchecked")
        Map<String, String> httpRepoMap = JsonUtils.getInstance()
                .readValue(JsonUtils.getInstance().valueToByteArray(virtualRepoDescriptor), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> mappedHttpRepoMap = JsonUtils.getInstance()
                .readValue(JsonUtils.getInstance().valueToByteArray(mappedVirtualDescriptor), Map.class);
        assertEquals(httpRepoMap, mappedHttpRepoMap);
        assertTransientFields(virtualRepoDescriptor, mappedVirtualDescriptor);
    }

    private void assertTransientFields(VirtualRepoDescriptor sourceVirtualRepoDescriptor,
            VirtualRepoDescriptor mappedVirtualDescriptor) {
        Assert.assertEquals(mappedVirtualDescriptor.isEnableNormalizedVersion(),
                sourceVirtualRepoDescriptor.isEnableNormalizedVersion());
    }

    @Test
    void defaultCdnRedirect() {
        VirtualRepoDescriptor virtualRepo = new VirtualRepoDescriptor();
        assertFalse(virtualRepo.isCdnRedirect());
    }

    @Test
    void defaultDownloadRedirect() {
        VirtualRepoDescriptor virtualRepo = new VirtualRepoDescriptor();
        assertFalse(virtualRepo.isDownloadRedirect());
    }
}