package org.artifactory.release.bundle.interceptor;


import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.rest.distribution.bundle.models.ReleaseBundleModel;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@Test
public class ReleaseBundleInterceptorsTest {

    @Mock
    private ArtifactoryApplicationContext artifactoryContext;

    @Mock
    private ReleaseBundleInterceptor releaseBundleInterceptor;

    @Spy
    @InjectMocks
    private ReleaseBundleInterceptorsImpl releaseBundleInterceptors;

    private AutoCloseable mocks;

    @BeforeMethod
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);

        Map<String, ReleaseBundleInterceptor> interceptors = new HashMap<>();
        interceptors.put(ReleaseBundleInterceptor.class.getName(), releaseBundleInterceptor);

        when(artifactoryContext.beansForType(ReleaseBundleInterceptor.class)).thenReturn(interceptors);
        when(artifactoryContext.beanForType(ReleaseBundleInterceptors.class)).thenReturn(releaseBundleInterceptors);
        releaseBundleInterceptors.setApplicationContext(artifactoryContext);
        releaseBundleInterceptors.init();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testOnAfterCloseTransaction() {
        ReleaseBundleModel releaseBundleModel = new ReleaseBundleModel();
        releaseBundleModel.setName("rb1");
        releaseBundleModel.setVersion("1.0.0");
        releaseBundleInterceptors.onAfterCloseTransaction(releaseBundleModel);
        verify(releaseBundleInterceptor).onAfterCloseTransaction(releaseBundleModel);
    }
}