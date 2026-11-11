package org.artifactory.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.artifactory.fs.RepoResource;
import org.artifactory.resource.RemoteRepoResource;

import jakarta.annotation.Nullable;
import java.util.Arrays;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RepoResourceUtils {

    public static boolean isContentEncoded(@Nullable RepoResource remoteResource) {
        boolean encodedCoding = false;
        if (remoteResource instanceof RemoteRepoResource) {
            Header[] allHeaders = ((RemoteRepoResource) remoteResource).getAllHeaders();
            if (allHeaders != null) {
                encodedCoding = Arrays.stream(allHeaders)
                        .map(NameValuePair::getName)
                        .anyMatch(HttpHeaders.CONTENT_ENCODING::equals);
            }
        }
        return encodedCoding;
    }
}
