package org.artifactory.api.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.mime.MimeType;
import org.jfrog.common.ArgUtils;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class ComponentDetailsTest {

    @Test
    public void testUnMarshall() throws IOException {
        ComponentDetails componentDetails = ComponentDetails.builder()
                .componentType(RepoType.Maven)
                .extension("jar")
                .mimeType("application/java-archive")
                .name("yoaz.jar")
                .version("1.0.x")
                .build();
        String jsonDetails = ComponentDetails.toJson(componentDetails);
        ArgUtils.requireNullOrJson(jsonDetails, jsonDetails + " is not a valid json");
        JsonNode jsonNode = new ObjectMapper().readTree(jsonDetails);
        assertEquals(jsonNode.get("extension").toString(), "\"jar\"");
        assertEquals(jsonNode.get("mimeType").toString(), "\"application/java-archive\"");
        assertEquals(jsonNode.get("version").toString(), "\"1.0.x\"");
        assertEquals(jsonNode.get("version").toString(), "\"1.0.x\"");
        assertEquals(jsonNode.get("componentType").toString(), "\"Maven\"");
    }

    @Test
    public void testMarshall() {
        String str =
                "{\"name\":\"yoaz.jar\",\"version\":\"1.0.x\",\"componentType\":\"Maven\",\"extension\":\"jar\"," +
                        "\"mimeType\":\"application/java-archive\"}";
        ComponentDetails componentDetails = ComponentDetails.fromJson(str);
        assertNotNull(componentDetails);
        assertEquals(componentDetails.getName(), "yoaz.jar");
        assertEquals(componentDetails.getVersion(), "1.0.x");
        assertEquals(componentDetails.getComponentType(), RepoType.Maven);
        assertEquals(componentDetails.getExtension(), "jar");
        assertEquals(componentDetails.getMimeType(), "application/java-archive");
    }

    @Test
    public void testMarshallDefaultMimeType() {
        String str = "{\"name\":\"yoaz.jar\",\"version\":\"1.0.x\",\"componentType\":\"Maven\",\"extension\":\"jar\"}";
        ComponentDetails componentDetails = ComponentDetails.fromJson(str);
        assertNotNull(componentDetails);
        assertEquals(componentDetails.getName(), "yoaz.jar");
        assertEquals(componentDetails.getVersion(), "1.0.x");
        assertEquals(componentDetails.getComponentType(), RepoType.Maven);
        assertEquals(componentDetails.getExtension(), "jar");
        assertEquals(MimeType.def.getType(), componentDetails.getMimeType());
    }

}