package org.artifactory.repo.remote.interceptor;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.repo.http.IdleConnectionMonitorService;
import org.artifactory.schedule.TaskService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.client.http.CloseableObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockSettings;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.artifactory.common.ConstantValues.jfrogUserAgentDefaultURL;
import static org.artifactory.common.ConstantValues.jfrogUserAgentDownloadURL;
import static org.artifactory.common.ConstantValues.jfrogUserAgentGetMetadataURL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * @author Hussein Alshqerat
 */
@ExtendWith(MockitoExtension.class)
class JFrogUserAgentServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private TaskService taskService;

    private JFrogUserAgentService jfrogUserAgentService;

    @BeforeEach
    public void setUp() throws NoSuchMethodException, IOException {
        jfrogUserAgentService = new JFrogUserAgentService(
                jfrogUserAgentDefaultURL.getString(), jfrogUserAgentDownloadURL.getString(),
                jfrogUserAgentGetMetadataURL.getString(), "0 0 0 0 0",
                true, true, taskService);
        ArtifactoryApplicationContext artifactoryContext = mock(ArtifactoryApplicationContext.class);
        MockSettings monitorServiceSettings = withSettings().extraInterfaces(CloseableObserver.class);
        IdleConnectionMonitorService monitorService = mock(IdleConnectionMonitorService.class, monitorServiceSettings);
        Cache<String, Boolean> denyListCache = CacheBuilder.newBuilder().build();
        denyListCache.put("host.com", true);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(ContextHelper.get().beanForType(IdleConnectionMonitorService.class)).thenReturn(monitorService);
    }

    @AfterEach
    public void tearDown() {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testFetchJFrogUserAgentURLSuccess() {
        jfrogUserAgentService.fetchJFrogUserAgentURL();
        assertEquals(jfrogUserAgentDefaultURL.getString(), jfrogUserAgentService.getUserAgent());
    }
}