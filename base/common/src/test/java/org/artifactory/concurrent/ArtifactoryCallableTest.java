package org.artifactory.concurrent;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ServerTracingSpan;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * @author Uriah Levy
 */
@Test
public class ArtifactoryCallableTest {

    @Mock
    ArtifactoryContext artifactoryContext;

    @Mock
    ArtifactoryHome artifactoryHome;

    @Mock
    ArtifactoryTracer tracer;

    @Mock
    ServerTracingSpan currentSpan;
    private AutoCloseable mocks;

    @BeforeClass
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactorySystemProperties properties =  new ArtifactorySystemProperties();
        when(artifactoryContext.getArtifactoryHome()).thenReturn(artifactoryHome);
        when(artifactoryContext.beanForType(ArtifactoryTracer.class)).thenReturn(tracer);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(properties);
        when(tracer.getCurrentThreadActiveSpan()).thenReturn(currentSpan);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = ".*Artifactory home.*")
    public void testArtifactoryCallable() throws Exception {
        String result = new ArtifactoryCallable<>(() -> {
            // Context is bound
            assertNotNull(ContextHelper.get());
            assertNotNull(ArtifactoryHome.get());
            return "foo";
        }, artifactoryContext, null).call();

        assertEquals(result, "foo");
        assertNull(ContextHelper.get());
        // throws
        ArtifactoryHome.get();
    }
}