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
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.common.JsonUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.util.ReflectionUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static com.google.gson.JsonParser.parseString;
import static org.artifactory.descriptor.repo.RandomObjectPopulator.createAndFill;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the LocalRepoDescriptor.
 *
 * @author Yossi Shaul
 */
@Test
public class LocalRepoDescriptorTest extends ArtifactoryHomeBoundTest {

    @Mock
    private CentralConfigDescriptor centralConfigDescriptor;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    public void defaultConstructor() {
        LocalRepoDescriptor localRepo = new LocalRepoDescriptor();
        assertNull(localRepo.getKey(), "Key should be null");
        assertNull(localRepo.getDescription(), "Description should be null");
        assertEquals(localRepo.getIncludesPattern(), "**/*", "Includes pattern should be **/*");
        assertNull(localRepo.getExcludesPattern(), "Excludes pattern should be null");
        assertNull(localRepo.getRepoLayout(), "Repo layout should be null");
        assertEquals(localRepo.getMaxUniqueSnapshots(), 0, "Max unique snapshot should be 0 by default");
        assertEquals(localRepo.getMaxUniqueTags(), 0, "Max unique tags should be 0 by default");
        assertEquals(localRepo.getSnapshotVersionBehavior(), SnapshotVersionBehaviorDescriptor.UNIQUE,
                "SnapshotVersionBehavior should be non-unique by default");
        assertTrue(localRepo.isSuppressPomConsistencyChecks(), "Default should suppress pom consistency checks");
        assertNotNull(localRepo.getPropertySets(), "Property sets list should not be null");
        assertEquals(localRepo.getChecksumPolicyType(), LocalRepoChecksumPolicyTypeDescriptor.CLIENT,
                "Client checksum should be the default");
        assertEquals(localRepo.getYumRootDepth(), 0, "The default YUM calculation depth should be zero.");
        assertNotEquals(localRepo.getType(), RepoType.YUM, "YUM calculation should be off by default.");
        assertNotEquals(localRepo.getType(), RepoType.NuGet, "NuGet should be off by default.");
        assertEquals(localRepo.getDockerApiVersion(), DockerApiVersion.V2,
                "Docker API version should be V2 by default.");
    }

    @Test
    public void testDescriptorToRepositoryConfigAndFromRepositoryConfigMapping() throws Exception {
        LocalRepoDescriptor sourceLocalRepoDescriptor = createAndFill(LocalRepoDescriptor.class);

        // Generating all types of package configs
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        for (RepoType value : RepoType.values()) {
            sourceLocalRepoDescriptor.buildPackageTypeConfig(value, packageTypeConfig);
        }

        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setName(sourceLocalRepoDescriptor.getRepoLayout().getName());
        KeyPairDescriptor primaryKeyPair = new KeyPairDescriptor();
        primaryKeyPair.setKey(sourceLocalRepoDescriptor.getPrimaryKeyPairDescriptor().getKey());
        KeyPairDescriptor secondaryKeyPair = new KeyPairDescriptor();
        secondaryKeyPair.setKey(sourceLocalRepoDescriptor.getSecondaryKeyPairDescriptor().getKey());

        PropertySet propertySet1 = new PropertySet();
        propertySet1.setName("propertySetRef1");
        PropertySet propertySet2 = new PropertySet();
        propertySet2.setName("propertySetRef2");

        when(centralConfigDescriptor.getRepoLayouts()).thenReturn(List.of(repoLayout));
        when(centralConfigDescriptor.getPropertySets()).thenReturn(List.of(propertySet1, propertySet2));
        when(centralConfigDescriptor.getKeyPairs()).thenReturn(List.of(primaryKeyPair, secondaryKeyPair));

        LocalRepoConfig sourceRepositoryConfig = (LocalRepoConfig) sourceLocalRepoDescriptor.toRepositoryConfig();
        Field packageTypeConfigField = ReflectionUtils.findField(LocalRepoConfig.class, "packageTypeConfig");
        Assert.assertNotNull(packageTypeConfigField);
        ReflectionUtils.makeAccessible(packageTypeConfigField);
        ReflectionUtils.setField(packageTypeConfigField, sourceRepositoryConfig, packageTypeConfig);

        LocalRepoDescriptor mappedLocalDescriptor =
                LocalRepoDescriptor.fromRepositoryConfig(centralConfigDescriptor, sourceRepositoryConfig);

        for (RepoType value : RepoType.values()) {
            mappedLocalDescriptor.setPackageTypeConfig(sourceRepositoryConfig.getPackageTypeConfig(), value);
        }
        String sourceLocalRepoDescriptorJson = JsonUtils.getInstance().writeValueAsString(sourceLocalRepoDescriptor);
        String mappedLocalRepoDescriptorJson = JsonUtils.getInstance().writeValueAsString(mappedLocalDescriptor);

        System.out.println(sourceLocalRepoDescriptorJson);
        System.out.println(mappedLocalRepoDescriptorJson);

        assertEquals(parseString(mappedLocalRepoDescriptorJson), parseString(sourceLocalRepoDescriptorJson));
        assertTransientFields(sourceLocalRepoDescriptor, mappedLocalDescriptor);
    }

    private void assertTransientFields(LocalRepoDescriptor sourceLocalRepoDescriptor,
            LocalRepoDescriptor mappedLocalDescriptor) {
        assertEquals(mappedLocalDescriptor.isEnableNormalizedVersion(),
                sourceLocalRepoDescriptor.isEnableNormalizedVersion());
    }

    /**
     * A brand-new/pre-existing repo that never touched Cargo-specific settings (no {@code cargoConfig} at all,
     * e.g. a repo persisted before this field existed) must report the sparse HTTP index as enabled - not the
     * legacy Git index - matching {@link CargoConfiguration}'s own default.
     */
    @Test
    public void isCargoInternalIndexDefaultsToTrueWhenUnset() {
        LocalRepoDescriptor localRepo = new LocalRepoDescriptor();

        assertTrue(localRepo.isCargoInternalIndex(), "Cargo repos should default to the sparse index");
    }

    @Test
    public void isCargoInternalIndexReflectsExplicitlySetValue() {
        LocalRepoDescriptor localRepo = new LocalRepoDescriptor();
        CargoConfiguration cargoConfig = new CargoConfiguration();
        cargoConfig.setCargoInternalIndex(false);
        localRepo.setCargoConfig(cargoConfig);

        assertEquals(localRepo.isCargoInternalIndex(), false, "Explicit cargoConfig value should be honored");
    }

    @Test
    public void isCargoInternalIndexReflectsExplicitlySetTrueValue() {
        LocalRepoDescriptor localRepo = new LocalRepoDescriptor();
        CargoConfiguration cargoConfig = new CargoConfiguration();
        cargoConfig.setCargoInternalIndex(true);
        localRepo.setCargoConfig(cargoConfig);

        assertTrue(localRepo.isCargoInternalIndex(), "Explicit cargoConfig value should be honored");
    }

    @Test
    public void testGridTopologyIdPreservation_fromRepositoryConfigToDescriptor() {
        LocalRepoConfig sourceRepoConfig = LocalRepoConfig.create("test-repo", RepoType.Generic);
        sourceRepoConfig.getBaseConfig().setGridTopologyId("topology-abc-123");

        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setName("simple-default");

        when(centralConfigDescriptor.getRepoLayouts()).thenReturn(List.of(repoLayout));
        when(centralConfigDescriptor.getPropertySets()).thenReturn(List.of());
        when(centralConfigDescriptor.getKeyPairs()).thenReturn(List.of());

        LocalRepoDescriptor descriptor = LocalRepoDescriptor.fromRepositoryConfig(centralConfigDescriptor,
                sourceRepoConfig);

        assertEquals("topology-abc-123", descriptor.getGridTopologyId());
    }

    @Test
    public void testGridTopologyIdPreservation_fromDescriptorToRepositoryConfig() {
        LocalRepoDescriptor sourceDescriptor = new LocalRepoDescriptor();
        sourceDescriptor.setKey("test-repo");
        sourceDescriptor.setType(RepoType.Generic);
        sourceDescriptor.setGridTopologyId("topology-xyz-789");

        LocalRepoConfig repoConfig = (LocalRepoConfig) sourceDescriptor.toRepositoryConfig();

        assertEquals("topology-xyz-789", repoConfig.getBaseConfig().getFederationConfig().getGridTopologyId());
    }
}
