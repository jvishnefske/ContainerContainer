package org.artifactory.request;

import org.artifactory.api.request.ArtifactoryHeaders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerManifestResponseTest {

    @Test
    void setHeader_whitelistedHeader_isStored() {
        DockerManifestResponse response = new DockerManifestResponse();
        response.setHeader(ArtifactoryHeaders.ORIGIN_REMOTE_PATH, "https://registry.example.com/v2");

        assertTrue(response.getHeaders().containsKey(ArtifactoryHeaders.ORIGIN_REMOTE_PATH));
        assertEquals("https://registry.example.com/v2",
                response.getHeaders().get(ArtifactoryHeaders.ORIGIN_REMOTE_PATH).iterator().next());
    }

    @Test
    void setHeader_nonWhitelistedHeader_isIgnored() {
        DockerManifestResponse response = new DockerManifestResponse();
        response.setHeader("Content-Range", "bytes 0-499/1234");
        response.setHeader("X-Whatever", "somevalue");

        assertFalse(response.getHeaders().containsKey("Content-Range"));
        assertFalse(response.getHeaders().containsKey("X-Whatever"));
    }

    @Test
    void setHeader_whitelistedHeader_isCaseInsensitive() {
        DockerManifestResponse response = new DockerManifestResponse();
        String lowercase = ArtifactoryHeaders.ORIGIN_REMOTE_PATH.toLowerCase();
        String uppercase = ArtifactoryHeaders.ORIGIN_REMOTE_PATH.toUpperCase();

        response.setHeader(lowercase, "https://registry.example.com/lower");
        response.setHeader(uppercase, "https://registry.example.com/upper");

        assertTrue(response.getHeaders().containsKey(ArtifactoryHeaders.ORIGIN_REMOTE_PATH));
        assertEquals(2, response.getHeaders().get(ArtifactoryHeaders.ORIGIN_REMOTE_PATH).size());
    }

    @Test
    void setHeader_blankValue_isIgnored() {
        DockerManifestResponse response = new DockerManifestResponse();
        response.setHeader(ArtifactoryHeaders.ORIGIN_REMOTE_PATH, "");
        response.setHeader(ArtifactoryHeaders.ORIGIN_REMOTE_PATH, "   ");
        response.setHeader(ArtifactoryHeaders.ORIGIN_REMOTE_PATH, null);

        assertFalse(response.getHeaders().containsKey(ArtifactoryHeaders.ORIGIN_REMOTE_PATH));
    }
}
