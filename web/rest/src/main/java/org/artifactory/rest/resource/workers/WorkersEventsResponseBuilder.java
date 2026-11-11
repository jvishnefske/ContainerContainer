package org.artifactory.rest.resource.workers;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.addon.worker.WorkerEventsModel;

import java.util.List;

/**
 * @author Bhanu Reddy
 */
public class WorkersEventsResponseBuilder {

    Response buildResponse(List<WorkerEventsModel> jsonData) {
        return Response.status(Response.Status.OK)
                .entity(jsonData)
                .type(MediaType.APPLICATION_JSON).build();
    }
}
