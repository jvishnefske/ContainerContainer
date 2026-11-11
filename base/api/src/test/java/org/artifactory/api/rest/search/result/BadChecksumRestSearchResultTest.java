package org.artifactory.api.rest.search.result;

import org.artifactory.checksum.ChecksumType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Amir Avidan
 */
class BadChecksumRestSearchResultTest {

    private BadChecksumRestSearchResult searchResult;

    @BeforeEach
    public void setUp() {
        searchResult = new BadChecksumRestSearchResult();
    }

    @Test
    void testAddResultAccordingToTypeMd5() {
        String uri = "http://example.com/file";
        String clientChecksum = "clientMd5";
        String serverChecksum = "serverMd5";

        searchResult.addResultAccordingToType(uri, clientChecksum, serverChecksum, ChecksumType.md5);

        assertFalse(searchResult.getResults().isEmpty(), "Results should not be empty.");
        assertEquals(uri, searchResult.getResults().iterator().next().getUri(), "Unexpected URI.");
        assertEquals(clientChecksum, searchResult.getResults().iterator().next().getClientMd5(),
                "Unexpected client MD5.");
        assertEquals(serverChecksum, searchResult.getResults().iterator().next().getServerMd5(),
                "Unexpected server MD5.");
    }

    @Test
    void testAddResultAccordingToTypeSha1() {
        String uri = "http://example.com/file";
        String clientChecksum = "clientSha1";
        String serverChecksum = "serverSha1";

        searchResult.addResultAccordingToType(uri, clientChecksum, serverChecksum, ChecksumType.sha1);

        assertFalse(searchResult.getResults().isEmpty(), "Results should not be empty.");
        assertEquals(uri, searchResult.getResults().iterator().next().getUri(), "Unexpected URI.");
        assertEquals(clientChecksum, searchResult.getResults().iterator().next().getClientSha1(),
                "Unexpected client SHA1.");
        assertEquals(serverChecksum, searchResult.getResults().iterator().next().getServerSha1(),
                "Unexpected server SHA1.");
    }

    @Test
    void testAddResultAccordingToTypeSha256() {
        String uri = "http://example.com/file";
        String clientChecksum = "clientSha256";
        String serverChecksum = "serverSha256";

        searchResult.addResultAccordingToType(uri, clientChecksum, serverChecksum, ChecksumType.sha256);
        assertFalse(searchResult.getResults().isEmpty(), "Results should not be empty.");
        assertEquals(uri, searchResult.getResults().iterator().next().getUri(), "Unexpected URI.");
        assertEquals(clientChecksum, searchResult.getResults().iterator().next().getClientSha2(),
                "Unexpected client SHA256.");
        assertEquals(serverChecksum, searchResult.getResults().iterator().next().getServerSha2(),
                "Unexpected server SHA256.");
    }

    @Test
    void testIsLimitExceeded() {
        searchResult.setLimitReached(true);
        assertTrue(searchResult.isLimitReached(), "Limit should be exceeded.");

        searchResult.setLimitReached(false);
        assertFalse(searchResult.isLimitReached(), "Limit should not be exceeded.");
    }
}