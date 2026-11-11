package org.artifactory.ui.rest.service.distribution;


import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.release.bundle.ReleaseArtifactInternalModel;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.api.rest.distribution.bundle.models.BundleReleaseNotes;
import org.artifactory.api.rest.distribution.bundle.models.ReleaseArtifact;
import org.artifactory.api.rest.distribution.bundle.models.ReleaseBundleModel;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.bundle.BundleType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.distribution.ReleaseBundleIModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class GetReleaseBundleServiceTest extends ArtifactoryHomeBoundTest {

    private AutoCloseable mocks;
    private static final String rbName = "RB";
    private static final String rbVersion = "1.0";

    private ObjectMapper mapper = new ObjectMapper();

    @Mock
    AddonsManager addonsManager;

    @Mock
    AuthorizationService authService;

    @BeforeMethod
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void cleanup() throws Exception {
        mocks.close();
    }

    @InjectMocks
    GetReleaseBundleService getReleaseBundleService = new GetReleaseBundleService();

    @Test(dataProvider = "releaseBundleModels")
    public void testExecute(boolean keep, BundleType rbType) throws IOException {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ReleaseBundleAddon releaseBundleAddon = mock(ReleaseBundleAddon.class);
        RestResponse response = new ArtifactoryRestResponse();

        when(addonsManager.addonByType(ReleaseBundleAddon.class)).thenReturn(releaseBundleAddon);
        when(request.getPathParamByKey("name")).thenReturn(rbName);
        when(request.getPathParamByKey("version")).thenReturn(rbVersion);
        when(request.getPathParamByKey("type")).thenReturn(rbType.name());
        when(releaseBundleAddon.getBundleModel(rbName, rbVersion, rbType)).
                thenReturn(createReleaseBundleModelForTest(keep, rbType));
        when(releaseBundleAddon.getReleaseArtifactsUsingAql(rbName, rbVersion, rbType))
                .thenReturn(List.of(createArtifactModel()));
        when(releaseBundleAddon.getStoringRepo(rbName, rbVersion, rbType))
                .thenReturn("release-bundles");
        when(authService.canDelete(any())).thenReturn(true);
        getReleaseBundleService.execute(request, response);
        ReleaseBundleIModel model = mapper.readValue((String) response.getIModel(), ReleaseBundleIModel.class);

        Assert.assertEquals(model.getName(), rbName);
        Assert.assertEquals(model.getVersion(), rbVersion);
        Assert.assertEquals(model.getKeep(), keep);
        Assert.assertEquals(model.getReleaseNotes().getContent(), "release notes");
    }

    private ReleaseArtifactInternalModel createArtifactModel() {
        ReleaseArtifactInternalModel resModel = new ReleaseArtifactInternalModel();
        resModel.setName("artifact");
        resModel.setPath("release-bundles");
        resModel.setSize(10);
        resModel.setCreatedBy("gavriel");
        return resModel;

    }

    private ReleaseBundleModel createReleaseBundleModelForTest(boolean keep, BundleType type) {
        ReleaseBundleModel resModel = new ReleaseBundleModel();
        resModel.setName(rbName);
        resModel.setVersion(rbVersion);
        resModel.setDescription("description");
        resModel.setReleaseNotes(new BundleReleaseNotes("release notes", "plain_text"));
        resModel.setStatus("complete");
        resModel.setArtifacts(List.of(new ReleaseArtifact()));
        resModel.setStoringRepo("release-bundles");
        resModel.setKeep(keep);
        resModel.setType(type.name());
        return resModel;
    }

    @DataProvider
    private Object[][] releaseBundleModels() {
        return new Object[][] {
                {true, BundleType.TARGET},
                {false, BundleType.TARGET},
                {false, BundleType.SOURCE}
        };
    }
}