package org.artifactory.ui.rest.service.admin.advanced.systemlogs;

import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.livelogs.LiveLogsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.StreamRestResponse;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetSysLiveLogsDataServiceTest {

    private static final AolService aolService = mock(AolService.class);

    @BeforeAll
    static void beforeAll() {
        ArtifactoryApplicationContext context = mock(ArtifactoryApplicationContext.class);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
        when(context.getArtifactoryHome()).thenReturn(artifactoryHome);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        File dir = mock(File.class);
        when(artifactoryHome.getLogDir()).thenReturn(dir);
        ArtifactoryHome.bind(artifactoryHome);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterAll
    static void afterAll() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void forbiddenLog() {
        var request = Mockito.mock(ArtifactoryRestRequest.class);
        LiveLogsService liveLogsService = mock(LiveLogsService.class);
        when(request.getQueryParamByKey(any())).thenReturn("access-service.log");
        when(aolService.isAol()).thenReturn(true);
        doNothing().when(liveLogsService).assertUserCanRefreshLog(any());
        GetSysLiveLogsDataService getSysLiveLogsDataService = new GetSysLiveLogsDataService(liveLogsService);
        RestResponse<StreamRestResponse> response = new ArtifactoryRestResponse<>();
        getSysLiveLogsDataService.execute(request, response);
        assertEquals(404, response.getResponseCode());
        assertEquals("{\"error\":\"Log named access-service.log not found!\"}", response.getEntity().toString());
    }
}