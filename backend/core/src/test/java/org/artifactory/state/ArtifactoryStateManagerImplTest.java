package org.artifactory.state;

import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.addon.license.VerificationResult;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.model.ArtifactoryServerRole;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Shay Bagants
 */
@Test
public class ArtifactoryStateManagerImplTest {

    private ArtifactoryStateManagerImpl stateManager;

    @Mock
    private ArtifactoryServersCommonService serverService;
    private AutoCloseable mocks;

    @BeforeMethod
    private void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        stateManager = new ArtifactoryStateManagerImpl();
        stateManager.serversService = serverService;
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "Current Artifactory node " +
            "last heartbeat is: 0. Stopping Artifactory start up ,another server running converting process.")
    public void testHandleErrorNoMemberRegisteredInDb() {
        Mockito.when(serverService.getCurrentMember()).thenReturn(null);
        VerificationResult result = VerificationResult.converting;
        stateManager.handleError(result);
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "Current Artifactory node " +
            "last heartbeat is: 9999. Stopping Artifactory start up ,another server running converting process.")
    public void testHandleError() {
        ArtifactoryServer artifactoryServer = new ArtifactoryServer("1", 1, "url", 0,
                ArtifactoryServerState.CONVERTING, ArtifactoryServerRole.MEMBER, 9999, "1.0.0", 1, 1,
                ArtifactoryRunningMode.HA, "a");
        Mockito.when(serverService.getCurrentMember()).thenReturn(artifactoryServer);
        VerificationResult result = VerificationResult.converting;
        stateManager.handleError(result);
    }
}