package org.artifactory.addon.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Bhanu Reddy
 */
public class WorkerEventsModelTest {

    @Test
    public void testSerialization() throws Exception {
        WorkerEventsModel.Meta meta = new WorkerEventsModel.Meta("description", "http://doc.url");
        WorkerEventsModel.Sample sample = new WorkerEventsModel.Sample("payload", "code");
        WorkerEventsModel model = new WorkerEventsModel("name", "service", meta, sample,
                "payloadDocumentation", true, true, "filterType",
                true, "executionRequestType");

        ObjectMapper mapper = new ObjectMapper();
        String jsonString = mapper.writeValueAsString(model);

        assertTrue(jsonString.contains("\"name\":\"name\""));
        assertTrue(jsonString.contains("\"service\":\"service\""));
        assertTrue(jsonString.contains("\"description\":\"description\""));
        assertTrue(jsonString.contains("\"docUrl\":\"http://doc.url\""));
        assertTrue(jsonString.contains("\"payload\":\"payload\""));
        assertTrue(jsonString.contains("\"code\":\"code\""));
    }

    @Test
    public void testDeserialization() throws Exception {
        String jsonString = "{\"name\":\"name\",\"service\":\"service\",\"meta\":{\"description\":\"description\"," +
                "\"docUrl\":\"http://doc.url\"},\"sample\":{\"payload\":\"payload\",\"code\":\"code\"}," +
                "\"payloadDocumentation\":\"payloadDocumentation\",\"supportProjects\":true,\"isAsync\":true," +
                "\"filterType\":\"filterType\",\"mandatoryFilter\":true," +
                "\"executionRequestType\":\"executionRequestType\"}";

        ObjectMapper mapper = new ObjectMapper();
        WorkerEventsModel model = mapper.readValue(jsonString, WorkerEventsModel.class);

        assertEquals("name", model.name());
        assertEquals("service", model.service());
        assertEquals("description", model.meta().description());
        assertEquals("http://doc.url", model.meta().docUrl());
        assertEquals("payload", model.sample().payload());
        assertEquals("code", model.sample().code());
        assertEquals("payloadDocumentation", model.payloadDocumentation());
        assertTrue(model.supportProjects());
        assertTrue(model.isAsync());
        assertEquals("filterType", model.filterType());
        assertTrue(model.mandatoryFilter());
        assertEquals("executionRequestType", model.executionRequestType());
    }
}
