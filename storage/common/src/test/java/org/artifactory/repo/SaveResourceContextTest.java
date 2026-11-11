package org.artifactory.repo;

import org.artifactory.fs.RepoResource;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class SaveResourceContextTest {

    @Test
    public void getPreCalculatedHeaders_returnsEmptyMap_whenNotSet() {
        SaveResourceContext ctx = new SaveResourceContext.Builder(mock(RepoResource.class), (InputStream) null).build();
        assertTrue(ctx.getPreCalculatedHeaders().isEmpty());
    }

    @Test
    public void getPreCalculatedHeaders_returnsMap_whenSet() {
        Map<String, String> headers = Map.of("X-Checksum-Sha1", "abc123");
        SaveResourceContext ctx = new SaveResourceContext.Builder(mock(RepoResource.class), (InputStream) null)
                .preCalculatedHeaders(headers)
                .build();
        assertEquals(ctx.getPreCalculatedHeaders(), headers);
    }
}
