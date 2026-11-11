package org.artifactory.aql.result;

import jakarta.annotation.Nonnull;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.artifactory.aql.model.AqlPermissionProvider;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.StringInputStream;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * @author Alexei Vainshtein
 */
@Test
public class AqlJsonFederatedStreamerTest extends ArtifactoryHomeBoundTest {

    private String json = "\n" +
            "{\n" +
            "\"results\" : [ {\n" +
            "  \"repo\" : \"test\",\n" +
            "  \"path\" : \".\",\n" +
            "  \"name\" : \"a-file2.txt\",\n" +
            "  \"type\" : \"file\",\n" +
            "  \"size\" : 23,\n" +
            "  \"created\" : \"2022-09-01T19:16:45.302Z\",\n" +
            "  \"created_by\" : \"admin\",\n" +
            "  \"modified\" : \"2022-09-01T19:16:44.601Z\",\n" +
            "  \"modified_by\" : \"admin\",\n" +
            "  \"updated\" : \"2022-09-01T19:16:45.304Z\"\n" +
            "} ],\n" +
            "\"range\" : {\n" +
            "  \"start_pos\" : 0,\n" +
            "  \"end_pos\" : 1,\n" +
            "  \"total\" : 1\n" +
            "}\n" +
            "}\n";

    private String emptyJson = "\n" +
            "{\n" +
            "\"results\" : [],\n" +
            "\"range\" : {\n" +
            "  \"start_pos\" : 0,\n" +
            "  \"end_pos\" : 0,\n" +
            "  \"total\" : 0\n" +
            "}\n" +
            "}\n";

    @Test
    public void testReadNext() throws IOException {
        CloseableHttpResponse response = createResponse(json);
        AqlPermissionProvider permissionProvider = mock(AqlPermissionProvider.class);
        String remoteUrl = "http://remoteUrl";
        try (AqlJsonFederatedStreamer streamer = new AqlJsonFederatedStreamer(response, permissionProvider,
                mock(AqlRepoProvider.class), remoteUrl)) {
            for (Row r = streamer.readNext(); r != null; r = streamer.readNext()) {
                assertResponse(r);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testReadNextEmptyJson() throws IOException {
        CloseableHttpResponse response = createResponse(emptyJson);
        AqlPermissionProvider permissionProvider = mock(AqlPermissionProvider.class);
        String remoteUrl = "http://remoteUrl";
        try (AqlJsonFederatedStreamer streamer = new AqlJsonFederatedStreamer(response, permissionProvider,
                mock(AqlRepoProvider.class), remoteUrl)) {
            for (Row r = streamer.readNext(); r != null; r = streamer.readNext()) {
                assertResponse(r);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testReadNextWithException() throws IOException {
        CloseableHttpResponse response = createResponse(emptyJson);
        AqlPermissionProvider permissionProvider = mock(AqlPermissionProvider.class);
        String remoteUrl = "http://remoteUrl";
        try (AqlJsonFederatedStreamer streamer = spy(new AqlJsonFederatedStreamer(response, permissionProvider,
                mock(AqlRepoProvider.class), remoteUrl))) {
            doThrow(new IOException()).when(streamer).readRow();
            Row r = streamer.readNext();
            assertNull(r);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    private CloseableHttpResponse createResponse(String json) throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        BasicStatusLine basicStatusLine = new BasicStatusLine(new ProtocolVersion("p", 1, 2), HttpStatus.SC_OK, "test");
        when(response.getStatusLine()).thenReturn(basicStatusLine);
        HttpEntity entity = mock(HttpEntity.class);
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(new StringInputStream(json));
        return response;
    }

    private void assertResponse(Row r) {
        assertNotNull(r);
        assertEquals(r.itemRepo, "test");
        assertEquals(r.itemName, "a-file2.txt");
    }
}