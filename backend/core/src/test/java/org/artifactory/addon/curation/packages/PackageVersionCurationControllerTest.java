package org.artifactory.addon.curation.packages;

import com.jfrog.ph.acl.exception.PackageBlockedByCurationException;
import com.jfrog.ph.acl.model.PackageDownloadContext;
import com.jfrog.ph.acl.model.curation.CurationRequest;
import com.jfrog.ph.acl.model.curation.CurationResponse;
import org.artifactory.ph.curation.PackagesCurationMetadataWrapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackageVersionCurationControllerTest {


    @Test
    void getCuratedFilePath() {
        String repoKey = "my-repo";
        String packagePath = "path/to/my-package";
        String expectedPath = ".jfrog/curated/path/to/my-package";


        PackageVersionCurationController<ObjectWrapper> packageVersionCurationController = getMockedController();

        String actualPath =
                packageVersionCurationController.getCuratedFilePath(new PackageDownloadContext(repoKey, packagePath));

        assertEquals(expectedPath, actualPath, "The curated file path should match the expected format.");
    }

    @Test
    void throwPackageBlockedByCurationException_defaultThrowsForbidden() {
        PackageVersionCurationController<ObjectWrapper> controller = getMockedController();

        PackageBlockedByCurationException ex = assertThrows(PackageBlockedByCurationException.class,
                () -> controller.throwPackageBlockedByCurationException("blocked by policy"));

        assertEquals("blocked by policy", ex.getPublicReason());
    }

    private static class ObjectWrapper extends PackagesCurationMetadataWrapper {
        public ObjectWrapper(Object metadata) {
            super(metadata);
        }
    }

    private PackageVersionCurationController<ObjectWrapper> getMockedController() {
        return new PackageVersionCurationController<ObjectWrapper>() {
            @Override
            public CurationRequest createMetadataCurationRequest(ObjectWrapper originMetadata, String repoKey,
                    String remoteUrl) {
                return null; // Not needed for this test
            }

            @Override
            public ObjectWrapper getCuratedMetadataModel(CurationResponse curationResponse,
                    ObjectWrapper originMetadata) {
                return null; // Not needed for this test
            }

            @Override
            public ObjectWrapper getMetadataModelOrigin(InputStream originMetadataStream) {
                return null; // Not needed for this test
            }

            @Override
            public InputStream toInputStream(ObjectWrapper metadata) {
                return null;
            }

            @Override
            public boolean shouldCheckForCuration(String path) {
                return false;
            }
        };
    }
}