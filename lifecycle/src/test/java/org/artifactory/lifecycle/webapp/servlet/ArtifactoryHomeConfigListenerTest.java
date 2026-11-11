package org.artifactory.lifecycle.webapp.servlet;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * @author Hezi Cohen
 */
public class ArtifactoryHomeConfigListenerTest {

    @Mock
    ServletContextEvent event;

    @Mock
    ServletContext servletContext;

    @Mock
    BasicConfigurationManager basicConfigurationManager;

    @Mock
    ArtifactoryTracer artifactoryTracer;

    @Mock
    ArtifactoryHome artifactoryHome;

    @Spy
    ArtifactoryHomeConfigListener artifactoryHomeConfigListener;
    private AutoCloseable mocks;

    @BeforeClass
    private void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(event.getServletContext()).thenReturn(servletContext);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void testContextInitialized() {
        Mockito.doReturn(artifactoryTracer).when(artifactoryHomeConfigListener)
                .getArtifactoryTracer();
        Mockito.doReturn(artifactoryHome).when(artifactoryHomeConfigListener)
                .getArtifactoryHome(servletContext);
        Mockito.doReturn(basicConfigurationManager).when(artifactoryHomeConfigListener)
                .getBasicConfigManagers(artifactoryTracer, artifactoryHome);

        artifactoryHomeConfigListener.contextInitialized(event);

        InOrder inOrder = inOrder(artifactoryHome, artifactoryHomeConfigListener, basicConfigurationManager);
        inOrder.verify(artifactoryHome).initDBProperties();
        inOrder.verify(artifactoryHome).initArtifactorySystemProperties();
        inOrder.verify(artifactoryHomeConfigListener).getBasicConfigManagers(artifactoryTracer, artifactoryHome);
        inOrder.verify(basicConfigurationManager).initialize();
    }
}