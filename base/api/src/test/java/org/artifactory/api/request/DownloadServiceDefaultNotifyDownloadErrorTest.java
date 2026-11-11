package org.artifactory.api.request;

import org.artifactory.request.ArtifactoryRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class DownloadServiceDefaultNotifyDownloadErrorTest {

    @Test
    void defaultNotifyDownloadError_returnsPassthroughResult() {
        DownloadService service = new DownloadService() {
            @Override
            public void process(ArtifactoryRequest request, ArtifactoryResponse response) throws IOException {
                // not used
            }
        };
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);

        DownloadErrorNotificationResult result = service.notifyDownloadError(request, 404, "missing");

        assertEquals(404, result.status());
        assertEquals("missing", result.message());
    }
}
