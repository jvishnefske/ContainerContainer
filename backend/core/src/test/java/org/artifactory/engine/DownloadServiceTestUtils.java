package org.artifactory.engine;

import lombok.experimental.UtilityClass;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.request.ArtifactoryRequest;

import java.util.Enumeration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@UtilityClass
public class DownloadServiceTestUtils {

    public static void mockIsRedirectFromCachingProxy(ArtifactoryRequest request, boolean redirectedFromCachingProxy) {
        if (redirectedFromCachingProxy) {
            Enumeration enumeration = mock(Enumeration.class);
            when(enumeration.hasMoreElements()).thenReturn(true);
            when(enumeration.nextElement()).thenReturn(ControlledCacheConstants.JFROG_CONTROLLED_CACHE_DOWNLOAD_VALUE);
            when(request.getHeaderValues(ArtifactoryHeaders.X_JFROG_CONTROLLED_CACHE_REQUEST)).thenReturn(enumeration);
        }
    }
}
