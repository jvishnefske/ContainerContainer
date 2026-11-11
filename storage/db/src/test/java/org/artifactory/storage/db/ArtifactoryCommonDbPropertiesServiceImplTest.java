package org.artifactory.storage.db;

import org.artifactory.common.config.db.ArtifactoryCommonDbPropertiesService;
import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.artifactory.storage.db.properties.service.ArtifactoryDbPropertiesService;
import org.artifactory.storage.db.properties.service.ArtifactoryDbPropertiesServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ArtifactoryCommonDbPropertiesServiceImplTest {

    private ArtifactoryDbPropertiesService dbPropertiesService;
    private ArtifactoryCommonDbPropertiesService artifactoryDbPropertiesService;

    @BeforeEach
    public void setup() {
        dbPropertiesService = Mockito.mock(ArtifactoryDbPropertiesServiceImpl.class);
        artifactoryDbPropertiesService = new ArtifactoryCommonDbPropertiesServiceImpl(dbPropertiesService);
    }

    @Test
    void getLatestDbVersionInfo() {
        long installDate = 111111L;
        String version = "test_version";
        long release = 1L;
        int revision = 2222;
        String productVersion = "test_product_version";

        DbVersionInfo versionInfo = new DbVersionInfo(installDate, version, revision,
                release, productVersion);
        Mockito.when(dbPropertiesService.getDbVersionInfo()).thenReturn(versionInfo);
        DbVersionInfo actual = artifactoryDbPropertiesService.getDbVersionInfo();
        Assertions.assertEquals(installDate, actual.getInstallationDate());
        Assertions.assertEquals(version, actual.getArtifactoryVersion());
        Assertions.assertEquals(release, actual.getArtifactoryRelease());
        Assertions.assertEquals(revision, actual.getArtifactoryRevision());
        Assertions.assertEquals(productVersion, actual.getArtifactoryProductVersion());
    }
}
