package org.artifactory.ui.rest.resource.artifacts.browse.treebrowser.tabs.views;

import com.jfrog.ph.acl.exception.PackageException;
import org.artifactory.ph.PackageResourceService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.alpine.AlpineArtifactInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;

/**
 * @author Aiswarya NJ
 */
class ViewAlpineResourceTest {
    @Mock
    private PackageResourceService packageResourceService;
    @Mock
    private AlpineArtifactInfo artifactInfo;

    private ViewAlpineResource viewAlpineResource;

    private AutoCloseable autoCloseable;

    @BeforeEach
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void viewAlpine_shouldCallPackageResourceService() throws PackageException {
        viewAlpineResource = new ViewAlpineResource(packageResourceService);
        viewAlpineResource.viewAlpine(artifactInfo);
        Mockito.verify(packageResourceService).getPackageInfo(any(), any());
    }
}
