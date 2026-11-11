package org.artifactory.api.request;

import jakarta.annotation.Nonnull;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;

import static org.testng.Assert.assertEquals;

public class IfNoneMatchTest {

    @DataProvider(name = "data")
    public Object[][] data() {
        return new Object[][] {
                {"bad", true},
                {"etag", false},
                {"\"bad\"", true},
                {"\"etag\"", false},
                {"W/\"bad\"", true},
                {"W/\"etag\"", false},
                {"W/bad", true},
                {"W/etag", false},
        };
    }

    @Test(dataProvider = "data")
    public void test(String header, boolean expected) {
        Request request = new Request(header);
        assertEquals(expected, request.isNoneMatch("etag"));
    }

    private static class Request extends ArtifactoryRequestBase {

        private final String header;

        public Request(String header) {
            this.header = header;
        }

        @Override
        public boolean isRecursive() {
            return false;
        }

        @Override
        public boolean isFromAnotherArtifactory() {
            return false;
        }

        @Override
        public boolean isHeadOnly() {
            return false;
        }

        @Override
        public long getLastModified() {
            return 0;
        }

        @Override
        public long getIfModifiedSince() {
            return 0;
        }

        @Override
        public boolean hasIfModifiedSince() {
            return false;
        }

        @Override
        public String getServletContextUrl() {
            return null;
        }

        @Override
        public String getUri() {
            return null;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return null;
        }

        @Override
        public long getContentLength() {
            return 0;
        }

        @Override
        public String getClientAddress() {
            return null;
        }

        @Override
        public Enumeration<String> getHeadersKeys() {
            return null;
        }

        @Override
        public Enumeration<String> getHeaderValues(@Nonnull String headerName) {
            return Collections.enumeration(Collections.singletonList(header));
        }
    }

}
