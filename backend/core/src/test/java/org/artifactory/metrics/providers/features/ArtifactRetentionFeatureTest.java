package org.artifactory.metrics.providers.features;

import com.google.gson.Gson;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.addon.retention.RetentionStats;
import org.artifactory.addon.retention.RetentionStatsDetails;
import org.artifactory.api.callhome.FeatureGroup;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.UnsupportedByLicenseException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
public class ArtifactRetentionFeatureTest extends ArtifactoryHomeBoundTest {

    private MockitoSession mockitoSession;
    private ArtifactRetentionFeature artifactRetentionFeature;

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ArtifactRetentionAddon artifactRetentionAddon;

    @BeforeMethod
    public void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();

        artifactRetentionFeature = new ArtifactRetentionFeature(addonsManager);
        when(addonsManager.addonByType(ArtifactRetentionAddon.class)).thenReturn(artifactRetentionAddon);
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    public void retentionDisabledNFeatureReturnsNull() {
        when(artifactRetentionAddon.isRetentionEnabled()).thenReturn(false);
        Assert.assertNull(artifactRetentionFeature.getFeature());
    }

    @Test
    public void isColdInstanceV1orV2TrueFeatureReturnsColdInstanceField() {
        when(artifactRetentionAddon.isRetentionEnabled()).thenReturn(true);
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(true);

        FeatureGroup feature = artifactRetentionFeature.getFeature();
        String featureJson = new Gson().toJson(feature);
        Assert.assertEquals(featureJson,
                "{\"name\":\"Artifactory On Ice\",\"attributes\":{\"Is_Cold_Instance\":true}}");
    }

    @Test
    public void artifactRetentionStatsNullFeatureReturnsNull() {
        when(artifactRetentionAddon.isRetentionEnabled()).thenReturn(true);
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        when(artifactRetentionAddon.getArtifactRetentionStats()).thenReturn(null);
        Assert.assertNull(artifactRetentionFeature.getFeature());
    }

    @Test
    public void artifactRetentionFeatureReturnsAllStats() {
        String expectedOutputAsJson = "{\"name\":\"Artifactory On Ice\",\"attributes\":{\"Is_Cold_Instance\":false," +
                "\"Total_Artifacts_Policies\":1,\"Last_Day_Archive_Artifacts_Policies_Runs\":2," +
                "\"Last_Day_Archived_Artifacts\":3,\"Last_Day_Archived_Artifacts_Size\":5," +
                "\"Last_Day_Archived_Artifacts_Failed\":4,\"Last_Day_Archived_Artifacts_Failed_Size\":6," +
                "\"Last_Day_Restore_Artifacts_Policies_Runs\":7,\"Last_Day_Restored_Artifacts\":8," +
                "\"Last_Day_Restored_Artifacts_Size\":10,\"Last_Day_Restored_Artifacts_Failed\":9," +
                "\"Last_Day_Restored_Artifacts_Failed_Size\":11,\"Total_Packages_Policies\":21," +
                "\"Last_Day_Archive_Packages_Policies_Runs\":22,\"Last_Day_Archived_Packages\":23," +
                "\"Last_Day_Archived_Packages_Size\":25,\"Last_Day_Archived_Packages_Failed\":24," +
                "\"Last_Day_Archived_Packages_Failed_Size\":26," +
                "\"Last_Day_Restore_Packages_Policies_Runs\":27,\"Last_Day_Restored_Packages\":28," +
                "\"Last_Day_Restored_Packages_Size\":30,\"Last_Day_Restored_Packages_Failed\":29," +
                "\"Last_Day_Restored_Packages_Failed_Size\":31}}";

        assertRetentionFeatureOutput(expectedOutputAsJson);
    }

    @Test
    public void onlyArtifactsIncludedWhenPackagesRetentionIsOff() {
        getBound().setProperty(ConstantValues.retentionEnabledForPackages, "false");
        String expectedOutputAsJson = "{\"name\":\"Artifactory On Ice\",\"attributes\":{\"Is_Cold_Instance\":false," +
                "\"Total_Artifacts_Policies\":1,\"Last_Day_Archive_Artifacts_Policies_Runs\":2," +
                "\"Last_Day_Archived_Artifacts\":3,\"Last_Day_Archived_Artifacts_Size\":5," +
                "\"Last_Day_Archived_Artifacts_Failed\":4,\"Last_Day_Archived_Artifacts_Failed_Size\":6," +
                "\"Last_Day_Restore_Artifacts_Policies_Runs\":7,\"Last_Day_Restored_Artifacts\":8," +
                "\"Last_Day_Restored_Artifacts_Size\":10,\"Last_Day_Restored_Artifacts_Failed\":9," +
                "\"Last_Day_Restored_Artifacts_Failed_Size\":11}}";

        assertRetentionFeatureOutput(expectedOutputAsJson);
    }

    private void assertRetentionFeatureOutput(String expectedOutputAsJson) {
        RetentionStatsDetails artifactsDetails = new RetentionStatsDetails(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        RetentionStatsDetails packagesDetails = new RetentionStatsDetails(21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31);
        when(artifactRetentionAddon.isRetentionEnabled()).thenReturn(true);
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        RetentionStats retentionStats = new RetentionStats(artifactsDetails, packagesDetails);
        when(artifactRetentionAddon.getArtifactRetentionStats()).thenReturn(retentionStats);
        FeatureGroup feature = artifactRetentionFeature.getFeature();
        String featureJson = new Gson().toJson(feature);
        Assert.assertEquals(featureJson, expectedOutputAsJson);
    }

    @Test
    public void getStatsThrowsExceptionArtifactRetentionFeatureReturnsNull() {
        when(artifactRetentionAddon.isRetentionEnabled()).thenReturn(true);
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        when(artifactRetentionAddon.getArtifactRetentionStats()).thenThrow(new UnsupportedByLicenseException("msg"));
        Assert.assertNull(artifactRetentionFeature.getFeature());
    }
}