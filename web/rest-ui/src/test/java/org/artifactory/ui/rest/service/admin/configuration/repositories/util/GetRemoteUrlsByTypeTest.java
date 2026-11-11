package org.artifactory.ui.rest.service.admin.configuration.repositories.util;


import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.config.RemoteRegistry;
import org.artifactory.repo.config.RepoConfigDefaultValues;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

/**
 * @author BarakH
 */
class GetRemoteUrlsByTypeTest {

    @Test
    void testGetRemoteUrlsByTypeWithNoInput() {
        GetRemoteUrlsByType remoteUrlsByType = Mockito.spy(new GetRemoteUrlsByType());
        ArtifactoryRestRequest request = Mockito.mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();

        try {
            remoteUrlsByType.execute(request, response);
            Assertions.fail("Expected BadRequestException");
        } catch (BadRequestException e) {
            Assertions.assertEquals("RepoType must be provided", e.getMessage());
        }
    }

    @Test
    void testGetRemoteUrlsByType() {
        GetRemoteUrlsByType remoteUrlsByType = Mockito.spy(new GetRemoteUrlsByType());
        ArtifactoryRestRequest request = Mockito.mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();
        Mockito.when(request.getPathParamByKey("repoType")).thenReturn(RepoType.Docker.getType());

        remoteUrlsByType.execute(request, response);
        List<RemoteRegistry> registryList = (List<RemoteRegistry>) response.getEntity();
        RemoteRegistry remoteRegistry = registryList.get(0);
        Assertions.assertEquals(RepoConfigDefaultValues.DefaultUrl.urlByType(RepoType.Docker), remoteRegistry.getUrl());
        Assertions.assertTrue(remoteRegistry.isDefault());
    }
}