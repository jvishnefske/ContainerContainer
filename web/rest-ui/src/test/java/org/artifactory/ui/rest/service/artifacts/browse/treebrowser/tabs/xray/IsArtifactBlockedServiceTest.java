package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.xray;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.util.ReflectionUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Field;

import static org.artifactory.repo.db.DbLocalRepo.DOWNLOAD_BLOCK_REASON_MESSAGE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * @author Andrey Tashlitsky
 */
public class IsArtifactBlockedServiceTest {
    private final String BLOCK_MESSAGE = "The artifact is blocked due to " + DOWNLOAD_BLOCK_REASON_MESSAGE + " for ";

    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private XrayAddon xrayAddon;

    private AutoCloseable autoCloseable;

    @BeforeMethod
    public void beforeMethod() {
        autoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void afterMethod() throws Exception {
        autoCloseable.close();
    }

    @Test(dataProvider = "testBlockedArtifactXrayStatusDataProvider")
    public void testBlockedArtifactXrayStatus(boolean xrayAliveByHeartbeat, String expectedMsg) {
        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xrayAddon);
        when(authorizationService.canRead(any())).thenReturn(true);
        when(xrayAddon.isDownloadBlocked(any())).thenReturn(true);
        when(xrayAddon.isXrayAliveByHeartbeat()).thenReturn(xrayAliveByHeartbeat);
        IsArtifactBlockedService isArtifactBlockedService = new IsArtifactBlockedService();
        Field f = ReflectionUtils.findField(IsArtifactBlockedService.class, "addonsManager");
        assertNotNull(f);
        ReflectionUtils.makeAccessible(f);
        ReflectionUtils.setField(f, isArtifactBlockedService, addonsManager);
        f = ReflectionUtils.findField(IsArtifactBlockedService.class, "authorizationService");
        assertNotNull(f);
        ReflectionUtils.makeAccessible(f);
        ReflectionUtils.setField(f, isArtifactBlockedService, authorizationService);
        RepoResource repoResource = mock(RepoResource.class);
        RepoPath repoPath = mock(RepoPath.class);
        when(repoResource.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getPath()).thenReturn("cool-path");
        when(repoPath.getRepoKey()).thenReturn("cool-key");
        ArtifactoryRestRequest artifactoryRestRequest = mock(ArtifactoryRestRequest.class);
        when(artifactoryRestRequest.getQueryParamByKey(any())).thenReturn("cool");
        ArtifactoryRestResponse restResponse = new ArtifactoryRestResponse();
        isArtifactBlockedService.execute(artifactoryRestRequest, restResponse);
        assertEquals(restResponse.getFeedbackMsg().getError(), expectedMsg);
    }

    @DataProvider
    private Object[][] testBlockedArtifactXrayStatusDataProvider() {
        return new Object[][] {
                {true, BLOCK_MESSAGE + "cool."},
                {false, BLOCK_MESSAGE + "cool. Xray is unavailable."},
        };
    }
}
