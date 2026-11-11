/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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
 */

package org.artifactory.common.home;

import com.jfrog.sysconf.SysConfig;
import com.jfrog.sysconf.SysConfigConstants;
import com.jfrog.sysconf.SysLayout;
import jakarta.annotation.Nonnull;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.test.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.alpinePackageHandlerSupport;
import static org.artifactory.common.ConstantValues.cocoapodsPackageHandlerSupport;
import static org.artifactory.common.ConstantValues.composerPackageHandlerSupport;
import static org.artifactory.common.ConstantValues.dotCacheMetadataFolder;
import static org.artifactory.common.ConstantValues.nugetPackageHandlerSupport;
import static org.artifactory.common.ConstantValues.opkgPackageHandlerSupport;
import static org.artifactory.common.ConstantValues.packageHandlerSupportedPackages;
import static org.artifactory.common.ConstantValues.permissionsCacheType;
import static org.artifactory.common.home.ArtifactoryHome.FORCE_DUPLICATE_REPLICATION_CONVERT_MARKER;
import static org.artifactory.permissions.PermissionsCacheType.Profiles.PERMISSIONS_CACHE_ACCESS;
import static org.artifactory.permissions.PermissionsCacheType.Profiles.PERMISSIONS_CACHE_LEGACY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author Yossi Shaul
 */
public class ArtifactoryHomeTest {

    private static final String SPRING_PROFILE_PROP_NAME = "spring.profiles.active";

    @AfterEach
    public void cleanSysProps() {
        final List<String> jfProps = System.getProperties().keySet().stream()
                .map(String::valueOf)
                .filter(propKey -> propKey.startsWith("jf."))
                .toList();
        jfProps.forEach(System::clearProperty);
        ArtifactoryHome.unbind();
    }

    @Test
    void testNoHaConfigured() {
        File homeDir = TestUtils.createTempDir(ArtifactoryHomeTest.class);
        ArtifactoryHome home = new ArtifactoryHome(homeDir.getAbsolutePath());
        assertFalse(home.isHaConfigured());
    }

    @Test
    void testHaConfigured() {
        File homeDir = TestUtils.createTempDir(ArtifactoryHomeTest.class);
        System.setProperty("jf.shared.node.haEnabled", "true");
        ArtifactoryHome home = new ArtifactoryHome(homeDir.getAbsolutePath());
        assertTrue(home.isHaConfigured());
    }

    @Test
    void readHomeFolderFromSysProperty() {
        File homeDir = TestUtils.createTempDir(ArtifactoryHomeTest.class);
        System.setProperty(SysConfigConstants.SYS_JF_PRODUCT_HOME, homeDir.getAbsolutePath());
        ArtifactoryHome home = new ArtifactoryHome(null);
        assertEquals(home.getHomeDir().getAbsolutePath(), homeDir.getAbsolutePath());
    }

    @Test
    void testFolderLocations() {
        File homeDir = TestUtils.createTempDir(ArtifactoryHomeTest.class);
        SysLayout layout = new SysLayout(homeDir, ArtifactoryHome.ARTIFACTORY_SERVICE_NAME);
        System.setProperty(SysConfigConstants.SYS_JF_PRODUCT_HOME, homeDir.getAbsolutePath());

        ArtifactoryHome home = new ArtifactoryHome(null);
        assertEquals(home.getHomeDir().getAbsolutePath(), homeDir.getAbsolutePath());
        assertEquals(home.getEtcDir().getAbsolutePath(), layout.getServiceEtc().getAbsolutePath());
        assertEquals(home.getSecurityDir().getAbsolutePath(),
                new File(layout.getServiceEtc(), SysLayout.SECURITY_DIR).getAbsolutePath());
        assertEquals(home.getDataDir().getAbsolutePath(), layout.getServiceData().getAbsolutePath());
        assertEquals(home.getBackupDir().getAbsolutePath(), layout.getServiceBackup().getAbsolutePath());
        assertEquals(home.getLogDir().getAbsolutePath(), layout.getServiceLog().getAbsolutePath());
        assertEquals(home.getTempWorkDir().getAbsolutePath(),
                Paths.get(layout.getServiceData().getAbsolutePath(), "tmp", "work").toString());
    }

    @Test
    void testCustomHaDirs() {
        File homeDir = TestUtils.createTempDir(ArtifactoryHomeTest.class);
        final String haDataDir = Paths.get(homeDir.getAbsolutePath(), "ha-data").toString();
        final String haBackupDir = Paths.get(homeDir.getAbsolutePath(), "ha-backup").toString();

        System.setProperty(SysConfigConstants.SYS_JF_PRODUCT_HOME, homeDir.getAbsolutePath());
        System.setProperty("jf.shared.node.contextUrl", "http://this.will.enable.ha");
        System.setProperty("jf.artifactory.node.haDataDir", haDataDir);
        System.setProperty("jf.artifactory.node.haDataBackup", haBackupDir);
        ArtifactoryHome home = new ArtifactoryHome(null);

        assertEquals(home.getDataDir().getAbsolutePath(), haDataDir);
        assertEquals(home.getBackupDir().getAbsolutePath(), haBackupDir);
    }

    @Test
    void testCustomCacheDir() {
        File homeDir = TestUtils.createTempDir(ArtifactoryHomeTest.class);
        ArtifactoryHome home = spy(new ArtifactoryHome(homeDir.getAbsolutePath()));
        ArtifactorySystemProperties systemProperties = mock(ArtifactorySystemProperties.class);
        when(home.getArtifactoryProperties()).thenReturn(systemProperties);
        ArtifactoryHome.bind(home);
        try {
            assertEquals(home.getCacheDir().getAbsolutePath(),
                    Paths.get(home.getDataDir().getAbsolutePath(), ".cache").toString());

            String dotCachePath = "/home/dir/dot/cache";
            when(systemProperties.getProperty(dotCacheMetadataFolder)).thenReturn(dotCachePath);
            assertEquals(home.getCacheDir().getAbsolutePath(), dotCachePath);

            dotCachePath = "/home/dir/dot/cache/";
            when(systemProperties.getProperty(dotCacheMetadataFolder)).thenReturn(dotCachePath);
            assertEquals(home.getCacheDir().getAbsolutePath(),
                    dotCachePath.substring(0, dotCachePath.lastIndexOf("/")));
        } finally {
            ArtifactoryHome.unbind();
        }
    }

    @Test
    void testForbiddenCustomHaDir() {
        File homeDir = TestUtils.createTempDir(ArtifactoryHomeTest.class);
        System.setProperty(SysConfigConstants.SYS_JF_PRODUCT_HOME, homeDir.getAbsolutePath());
        ArtifactoryHome home = new ArtifactoryHome(null);
        final IllegalArgumentException exception = Assertions.assertThrowsExactly(
                IllegalArgumentException.class, () -> home.getHAAwareSubDir("nono", new File(homeDir, "ha-nono")));
        assertTrue(exception.getMessage().matches(".*only supported for 'data' and 'backup'.*"));

    }

    @Test
    void testGetForceDuplicateReplicationConvertFile() {
        File homeDir = TestUtils.createTempDir(ArtifactoryHomeTest.class);
        homeDir.deleteOnExit();
        ArtifactoryHome home = new ArtifactoryHome(homeDir.getAbsolutePath());
        assertEquals(home.getForceDuplicateReplicationConvertFile().getAbsolutePath(),
                Paths.get(home.getEtcDir().getAbsolutePath(), FORCE_DUPLICATE_REPLICATION_CONVERT_MARKER).toFile()
                        .getAbsolutePath());
    }

    @ParameterizedTest
    @MethodSource("springProfilesData")
    void testProfilesSetCorrect(List<String> packages, String existing, String expected) {
        File homeDir = TestUtils.createTempDir(ArtifactoryHomeTest.class);
        ArtifactoryHome home = spy(new ArtifactoryHome(homeDir.getAbsolutePath()));
        ArtifactorySystemProperties systemProperties = mock(ArtifactorySystemProperties.class);
        when(systemProperties.getListProperty(packageHandlerSupportedPackages)).thenReturn(packages);
        when(systemProperties.getProperty(permissionsCacheType)).thenReturn("");
        when(home.getArtifactoryProperties()).thenReturn(systemProperties);
        ArtifactoryHome.bind(home);
        String original = System.getProperty(SPRING_PROFILE_PROP_NAME);
        System.setProperty(SPRING_PROFILE_PROP_NAME, Objects.requireNonNullElse(existing, ""));
        home.setActiveSpringProfiles(systemProperties);
        try {
            assertEquals(expected, System.getProperty(SPRING_PROFILE_PROP_NAME));
        } finally {
            ArtifactoryHome.unbind();
            System.setProperty(SPRING_PROFILE_PROP_NAME, Objects.requireNonNullElse(original, ""));
        }
    }

    @Test
    void testProfilesSetCorrectWithIndividualPkgConfig() {
        File homeDir = TestUtils.createTempDir(ArtifactoryHomeTest.class);
        ArtifactoryHome home = spy(new ArtifactoryHome(homeDir.getAbsolutePath()));
        ArtifactorySystemProperties systemProperties = mock(ArtifactorySystemProperties.class);
        when(systemProperties.getListProperty(packageHandlerSupportedPackages)).thenReturn(List.of("NUGET"));
        when(systemProperties.getBooleanProperty(alpinePackageHandlerSupport)).thenReturn(true);
        when(systemProperties.getBooleanProperty(cocoapodsPackageHandlerSupport)).thenReturn(false);
        when(systemProperties.getBooleanProperty(nugetPackageHandlerSupport)).thenReturn(false);
        when(systemProperties.getProperty(permissionsCacheType)).thenReturn("");
        when(home.getArtifactoryProperties()).thenReturn(systemProperties);
        ArtifactoryHome.bind(home);
        String original = System.getProperty(SPRING_PROFILE_PROP_NAME);
        System.setProperty(SPRING_PROFILE_PROP_NAME, "");
        home.setActiveSpringProfiles(systemProperties);
        try {
            assertEquals("nugetNew,alpineNew," + PERMISSIONS_CACHE_LEGACY,
                    System.getProperty(SPRING_PROFILE_PROP_NAME));
        } finally {
            ArtifactoryHome.unbind();
            System.setProperty(SPRING_PROFILE_PROP_NAME, Objects.requireNonNullElse(original, ""));
        }
    }

    @Test
    void testProfilesSetCorrectWithOnlyIndividualPkgConfig() {
        File homeDir = TestUtils.createTempDir(ArtifactoryHomeTest.class);
        ArtifactoryHome home = spy(new ArtifactoryHome(homeDir.getAbsolutePath()));
        ArtifactorySystemProperties systemProperties = mock(ArtifactorySystemProperties.class);
        when(systemProperties.getListProperty(packageHandlerSupportedPackages)).thenReturn(List.of(""));
        when(systemProperties.getBooleanProperty(alpinePackageHandlerSupport)).thenReturn(true);
        when(systemProperties.getBooleanProperty(cocoapodsPackageHandlerSupport)).thenReturn(true);
        when(systemProperties.getBooleanProperty(nugetPackageHandlerSupport)).thenReturn(false);
        when(systemProperties.getBooleanProperty(opkgPackageHandlerSupport)).thenReturn(true);
        when(systemProperties.getBooleanProperty(composerPackageHandlerSupport)).thenReturn(true);
        when(systemProperties.getProperty(permissionsCacheType)).thenReturn("");
        when(home.getArtifactoryProperties()).thenReturn(systemProperties);
        ArtifactoryHome.bind(home);
        String original = System.getProperty(SPRING_PROFILE_PROP_NAME);
        System.setProperty(SPRING_PROFILE_PROP_NAME, "");
        home.setActiveSpringProfiles(systemProperties);
        try {
            assertTrue(System.getProperty(SPRING_PROFILE_PROP_NAME).contains("alpineNew"));
            assertTrue(System.getProperty(SPRING_PROFILE_PROP_NAME).contains("cocoapodsNew"));
            assertTrue(System.getProperty(SPRING_PROFILE_PROP_NAME).contains("opkgNew"));
            assertTrue(System.getProperty(SPRING_PROFILE_PROP_NAME).contains("composerNew"));
        } finally {
            ArtifactoryHome.unbind();
            System.setProperty(SPRING_PROFILE_PROP_NAME, Objects.requireNonNullElse(original, ""));
        }
    }

    static Stream<Arguments> springProfilesData() {
        return Stream.of(
                Arguments.of(List.of("NUGET"), "", "nugetNew," + PERMISSIONS_CACHE_LEGACY),
                Arguments.of(List.of("NUGET"), null, "nugetNew," + PERMISSIONS_CACHE_LEGACY),
                Arguments.of(List.of("NUGET"), "existing",
                        "existing,nugetNew," + PERMISSIONS_CACHE_LEGACY),
                Arguments.of(List.of("NUGET"), "existing,existing1",
                        "existing,existing1,nugetNew," + PERMISSIONS_CACHE_LEGACY),
                Arguments.of(List.of(), "existing," + PERMISSIONS_CACHE_LEGACY, "existing," + PERMISSIONS_CACHE_LEGACY)
        );
    }

    @Test
    void testAddPermissionsCacheProfile() {
        File homeDir = TestUtils.createTempDir(ArtifactoryHomeTest.class);
        ArtifactoryHome home = spy(new ArtifactoryHome(homeDir.getAbsolutePath()));
        ArtifactorySystemProperties systemProperties = mock(ArtifactorySystemProperties.class);
        when(systemProperties.getProperty(permissionsCacheType)).thenReturn("access");
        when(home.getArtifactoryProperties()).thenReturn(systemProperties);
        ArtifactoryHome.bind(home);
        String original = System.getProperty(SPRING_PROFILE_PROP_NAME);
        System.setProperty(SPRING_PROFILE_PROP_NAME, "");
        home.setActiveSpringProfiles(systemProperties);
        try {
            assertEquals(PERMISSIONS_CACHE_ACCESS, System.getProperty(SPRING_PROFILE_PROP_NAME));
        } finally {
            ArtifactoryHome.unbind();
            System.setProperty(SPRING_PROFILE_PROP_NAME, Objects.requireNonNullElse(original, ""));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getBooleanProperty_Success(boolean value) {
        String key = "some-key";
        SysConfig sysConfig = mockSysConfigForArtifactoryHome();
        when(sysConfig.getBoolean(eq(key), anyBoolean())).thenReturn(value);
        assertEquals(value, ArtifactoryHome.getBooleanFromSysConfig(key, value));
    }

    @Test
    void getBooleanProperty_RuntimeException() {
        String key = "some-key";
        boolean defaultValue = false;
        SysConfig sysConfig = mockSysConfigForArtifactoryHome();
        when(sysConfig.getBoolean(eq(key), anyBoolean())).thenThrow(new RuntimeException());
        assertEquals(defaultValue, ArtifactoryHome.getBooleanFromSysConfig(key, defaultValue));
    }

    @Nonnull
    private static SysConfig mockSysConfigForArtifactoryHome() {
        SysConfig sysConfig = mock(SysConfig.class);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        ArtifactoryHome.bind(artifactoryHome);
        return sysConfig;
    }
}
