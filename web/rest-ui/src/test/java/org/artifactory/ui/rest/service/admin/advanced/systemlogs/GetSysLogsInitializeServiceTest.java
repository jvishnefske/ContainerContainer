package org.artifactory.ui.rest.service.admin.advanced.systemlogs;

import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.advanced.systemlogs.SystemLogsInitialize;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Test
public class GetSysLogsInitializeServiceTest extends ArtifactoryHomeBoundTest {

    private AolService aolService;

    @BeforeMethod
    public void setup() {
        ArtifactoryContext context = Mockito.mock(ArtifactoryContext.class);
        aolService = Mockito.mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    public void testSystemLogsInitialize() {
        assertLogsOutput(
                Arrays.asList("artifactory-service.log", "artifactory-access.log", "artifactory-import-export.log",
                        "artifactory-request.log", "console.log"));
    }

    public void testSystemLogsInitializeOnSaas() {
        when(aolService.isAol()).thenReturn(true);
        assertLogsOutput(
                Arrays.asList("artifactory-service.log", "artifactory-access.log", "artifactory-import-export.log",
                        "artifactory-request.log"));
    }

    private void assertLogsOutput(List<String> expectedLogs) {
        ArtifactoryRestRequest request = Mockito.mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();

        GetSysLogsInitializeService sysLogsInitializeService = new GetSysLogsInitializeService();
        sysLogsInitializeService.execute(request, response);

        SystemLogsInitialize systemLogsInitialize = (SystemLogsInitialize) response.getIModel();
        List<String> logFiles = systemLogsInitialize.getLogs();

        assertNotNull(logFiles);
        assertTrue(logFiles.containsAll(expectedLogs));
        assertEquals(logFiles.size(), expectedLogs.size());
    }
}