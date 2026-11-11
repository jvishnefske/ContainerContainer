package org.artifactory.rest.resource.workers;

import jakarta.ws.rs.core.Response;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.worker.WorkerEventsModel;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.exception.WorkerEventsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Bhanu Reddy
 */
@ExtendWith(MockitoExtension.class)
public class WorkersResourceTest {

    @Mock
    private AddonsManager addonsManager;

    @Mock
    private WorkersAddon workersAddon;

    @InjectMocks
    private WorkersResource workersResource;

    @BeforeEach
    public void setUp() {
        when(addonsManager.addonByType(WorkersAddon.class)).thenReturn(workersAddon);
    }

    @Test
    public void testGetWorkers() throws IOException {
        List<WorkerEventsModel> workers = new ArrayList<WorkerEventsModel>();
        WorkerEventsModel beforeDownload = new WorkerEventsModel("test", "service", null,
                null, "payloadDocumentation", true, true,
                "filterType", true, "beforeDownloadRequest");
        workers.add(beforeDownload);
        when(workersAddon.getAvailableEvents()).thenReturn(workers);

        Response response = workersResource.getWorkers();

        assertEquals(200, response.getStatus());
        verify(addonsManager, times(1)).addonByType(WorkersAddon.class);
        verify(workersAddon, times(1)).getAvailableEvents();
    }

    @Test
    public void testGetWorkersWhenExceptionIsThrown() throws IOException {
        List<WorkerEventsModel> workers = new ArrayList<WorkerEventsModel>();
        WorkerEventsModel beforeDownload = new WorkerEventsModel("test", "service", null,
                null, "payloadDocumentation", true, true,
                "filterType", true, "beforeDownloadRequest");
        workers.add(beforeDownload);
        when(workersAddon.getAvailableEvents()).thenThrow(new WorkerEventsException("failed to parse json"));
        assertThrows(RuntimeException.class, workersResource::getWorkers);
        verify(addonsManager, times(1)).addonByType(WorkersAddon.class);
        verify(workersAddon, times(1)).getAvailableEvents();
    }

}