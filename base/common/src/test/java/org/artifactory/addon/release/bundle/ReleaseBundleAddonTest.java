package org.artifactory.addon.release.bundle;

import org.artifactory.api.release.bundle.ReleaseBundleSearchFilter;
import org.artifactory.api.rest.release.ReleaseBundleRequest;
import org.artifactory.api.rest.release.SourceReleaseBundleRequest;
import org.artifactory.bundle.BundleType;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.util.UnsupportedByLicenseException;
import org.jfrog.model.api.releasebundle.ReleaseBundleOpenTransactionRequest;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;

/**
 * @author Eli Skoran 20/05/2020
 */
@Test
public class ReleaseBundleAddonTest {
    private final ReleaseBundleAddon addon = new ReleaseBundleAddon() {
        @Override
        public void exportTo(ExportSettings exportSettings) {

        }

        @Override
        public void importFrom(ImportSettings importSettings) {

        }

        @Override
        public boolean isDefault() {
            return false;
        }
    };

    @Test
    public void testDefaults() {
        assertThrows(UnsupportedByLicenseException.class,
                () -> addon.executeReleaseBundleRequest(mock(ReleaseBundleRequest.class), false, "projectKey"));
        assertThrows(UnsupportedByLicenseException.class, () -> addon.verifyReleaseBundleSignature("", ""));
        assertThrows(UnsupportedByLicenseException.class,
                () -> addon.createBundleTransaction(mock(ReleaseBundleOpenTransactionRequest.class)));
        assertThrows(UnsupportedByLicenseException.class, () -> addon.closeBundleTransaction(""));
        assertThrows(UnsupportedByLicenseException.class,
                () -> addon.closeBundleTransactionAsync("", 0, Collections.emptyList(), false));
        assertThrows(UnsupportedByLicenseException.class, () -> addon.checkCloseTransactionStatus(""));
        assertThrows(UnsupportedByLicenseException.class, () -> addon.checkCloseTransactionStatus("", ""));
        assertThrows(UnsupportedByLicenseException.class, () -> addon.getAllBundles(BundleType.SOURCE));
        assertThrows(UnsupportedByLicenseException.class,
                () -> addon.getCompletedBundlesLastVersion(mock(ReleaseBundleSearchFilter.class)));
        assertThrows(UnsupportedByLicenseException.class,
                () -> addon.getBundlesByReposAndPatterns(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                        null));
        assertThrows(UnsupportedByLicenseException.class,
                () -> addon.getBundleVersions(mock(ReleaseBundleSearchFilter.class)));
        assertThrows(UnsupportedByLicenseException.class, () -> addon.getBundleJson("", "", BundleType.SOURCE));
        assertThrows(UnsupportedByLicenseException.class, () -> addon.getBundleSignedJws("", "", BundleType.SOURCE));
        assertThrows(UnsupportedByLicenseException.class, () -> addon.getBundleVersions("", BundleType.SOURCE));
        assertThrows(UnsupportedByLicenseException.class,
                () -> addon.deleteReleaseBundle("", "", BundleType.TARGET));
        assertThrows(UnsupportedByLicenseException.class, () -> addon.getBundleModel("", "", BundleType.SOURCE));
        assertThrows(UnsupportedByLicenseException.class,
                () -> addon.storeBundle(mock(SourceReleaseBundleRequest.class), "projectKey"));
        assertThrows(UnsupportedByLicenseException.class, () -> addon.getStoringRepo("", "", BundleType.SOURCE));
        assertThrows(UnsupportedByLicenseException.class, () -> addon.getBundleStatus("", "", BundleType.SOURCE));
        assertThrows(UnsupportedByLicenseException.class,
                () -> addon.getReleaseArtifactsUsingAql("", "", BundleType.SOURCE));
        assertThrows(UnsupportedByLicenseException.class,
                () -> addon.getBundleSourceServiceId("rb", "v2", BundleType.TARGET));
        assertThrows(NullPointerException.class,
                () -> addon.getBundleSourceServiceId(null, "v2", BundleType.TARGET));
        assertThrows(NullPointerException.class,
                () -> addon.getBundleSourceServiceId("rb", null, BundleType.TARGET));
        assertThrows(NullPointerException.class,
                () -> addon.getBundleSourceServiceId(null, null, BundleType.TARGET));
        assertNull(addon.getReleaseBundlesConfig());
    }
}