package org.artifactory.rest.services.cleanup;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.cleanup.CleanupPolicyAddon;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Oleksandr Legenkiy
 */
class DeleteCleanupPackagePolicyServiceTest {

    @Test
    void testExecute() {
        CleanupPolicyAddon addonMock = mock(CleanupPolicyAddon.class);
        doNothing().when(addonMock).deletePackagePolicy("key");
        AddonsManager addonsManagerMock = mock(AddonsManager.class);
        when(addonsManagerMock.addonByType(CleanupPolicyAddon.class)).thenReturn(addonMock);

        var requestMock = (ArtifactoryRestRequest<String>) mock(ArtifactoryRestRequest.class);
        when(requestMock.getImodel()).thenReturn("key");

        var response = new ArtifactoryRestResponse<>();
        var service = new DeleteCleanupPackagePolicyService(addonsManagerMock);
        service.execute(requestMock, response);
        verify(addonMock, times(1)).deletePackagePolicy("key");
        assertEquals("Package Cleanup Policy key was deleted successfully.", response.getFeedbackMsg().getInfo());
        assertEquals(HttpServletResponse.SC_OK, response.getResponseCode());
    }
}