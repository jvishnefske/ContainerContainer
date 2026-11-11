package org.artifactory.devopsagent.errorcatalog.service;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;

@Test
public class ErrorCatalogBeansConfigTest {

    @Mock
    private ArtifactoryHome home;
    @Mock
    private ArtifactorySystemProperties props;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(home.getArtifactoryProperties()).thenReturn(props);
        ArtifactoryHome.bind(home);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test
    public void testErrorCatalogEventBuffer() {
        assertNotNull(new ErrorCatalogBeansConfig().errorCatalogEventBuffer());
    }

    @Test
    public void testSignatureToTemplateIdCache() {
        assertNotNull(new ErrorCatalogBeansConfig().signatureToTemplateIdCache());
    }
}
