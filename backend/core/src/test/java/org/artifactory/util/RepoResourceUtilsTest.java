package org.artifactory.util;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.artifactory.resource.RemoteRepoResource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.apache.http.HttpHeaders.ACCEPT_ENCODING;
import static org.apache.http.HttpHeaders.CONTENT_ENCODING;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepoResourceUtilsTest {

    @Test
    void testisContentEncoded() {
        RemoteRepoResource remoteRepoResource = mock(RemoteRepoResource.class);
        Header[] headers = {new BasicHeader(CONTENT_ENCODING, "gzip")};
        when(remoteRepoResource.getAllHeaders()).thenReturn(headers);
        Assertions.assertTrue(RepoResourceUtils.isContentEncoded(remoteRepoResource));
    }

    @Test
    void testisContentEncodedWithoutHeader() {
        RemoteRepoResource remoteRepoResource = mock(RemoteRepoResource.class);
        Header[] headers = {new BasicHeader(ACCEPT_ENCODING, "gzip")};
        when(remoteRepoResource.getAllHeaders()).thenReturn(headers);
        Assertions.assertFalse(RepoResourceUtils.isContentEncoded(remoteRepoResource));
    }

    @Test
    void testisContentEncodedWithNull() {
        Assertions.assertFalse(RepoResourceUtils.isContentEncoded(null));
    }

}