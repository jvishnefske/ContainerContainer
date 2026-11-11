package org.artifactory.ui.utils;

import org.artifactory.api.artifact.AlpineArtifactInfo;
import org.artifactory.api.artifact.ArtifactInfo;
import org.artifactory.api.artifact.CranUnitInfo;
import org.artifactory.api.artifact.DebianArtifactInfo;
import org.artifactory.api.artifact.DebianSourceArtifactInfo;
import org.artifactory.api.artifact.UnitInfo;
import org.artifactory.api.artifact.VagrantArtifactInfo;
import org.artifactory.api.maven.MavenService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.mime.NamingUtils;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.artifacts.deploy.UploadArtifactInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;

class UnitUtilsTest extends ArtifactoryHomeBoundTest {
    @ParameterizedTest
    @MethodSource("testPopulateUnitInfoProvider")
    void testPopulateUnitInfo(File uniqueFile, String originalFileName,
            UploadArtifactInfo uploadArtifactInfo,
            MavenService mavenService, RepoType repoType, boolean isJarVariant,
            Class<? extends UnitInfo> expectedType) {
        try (MockedStatic<NamingUtils> namingUtilsMockedStatic = org.mockito.Mockito.mockStatic(NamingUtils.class)) {
            namingUtilsMockedStatic.when(() -> NamingUtils.isJarVariant(anyString())).thenReturn(isJarVariant);
            UnitUtils.populateUnitInfo(uniqueFile, originalFileName, uploadArtifactInfo, mavenService, repoType);
        }
        Assertions.assertNotNull(uploadArtifactInfo.getUnitInfo());
        Assertions.assertInstanceOf(expectedType, uploadArtifactInfo.getUnitInfo());
    }

    public static List<Arguments> testPopulateUnitInfoProvider() {
        return List.of(
                Arguments.of(new File("test.deb"), "test.deb", new UploadArtifactInfo(), null,
                        RepoType.Debian, false, DebianArtifactInfo.class),
                Arguments.of(new File("test.dsc"), "test.dsc", new UploadArtifactInfo(), null,
                        RepoType.Debian, false, DebianSourceArtifactInfo.class),
                Arguments.of(new File("test.apk"), "test.apk", new UploadArtifactInfo(), null,
                        RepoType.Alpine, false, AlpineArtifactInfo.class),
                Arguments.of(new File("test.vagrant"), "test.box", new UploadArtifactInfo(), null,
                        RepoType.Vagrant,
                        false, VagrantArtifactInfo.class),
                Arguments.of(new File("test.cran"), "test.cran", new UploadArtifactInfo(), null,
                        RepoType.CRAN, false, CranUnitInfo.class),
                Arguments.of(new File("test.js"), "test.js", new UploadArtifactInfo(), null,
                        RepoType.Npm, false, ArtifactInfo.class)
        );
    }
}