package org.artifactory.util;

import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyMethod;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Omri Ziv
 */
@Test
public class ConfReverseProxyHelperTest {

    @Mock
    ArtifactoryContext context;
    @Mock
    AolService aolService;
    @Mock
    CentralConfigService centralConfigService;
    @Mock
    CentralConfigDescriptor centralConfigDescriptor;
    @Mock
    ArtifactorySystemProperties artifactorySystemProperties;
    @Mock
    ArtifactoryHome artifactoryHome;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(aolService.skipDockerDns()).thenReturn(false);
        when(artifactoryHome.getArtifactoryProperties())
                .thenReturn(artifactorySystemProperties);
        ArtifactoryHome.bind(artifactoryHome);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test(dataProvider = "methods")
    public void testGetReverseProxyMethod(ReverseProxyDescriptor current, boolean skipDns,
            ReverseProxyMethod expected) {
        when(centralConfigDescriptor.getCurrentReverseProxy())
                .thenReturn(current);
        if (skipDns) {
            when(aolService.skipDockerDns()).thenReturn(true);
        }

        assertEquals(expected, ConfReverseProxyHelper.getReverseProxyMethod());
    }

    @DataProvider
    private Object[][] methods() {
        return new Object[][] {
                {createReverseProxyMock(ReverseProxyMethod.SUBDOMAIN), false, ReverseProxyMethod.SUBDOMAIN},
                {createReverseProxyMock(ReverseProxyMethod.NOVALUE), false, ReverseProxyMethod.NOVALUE},
                {createReverseProxyMock(ReverseProxyMethod.REPOPATHPREFIX), false, ReverseProxyMethod.REPOPATHPREFIX},
                {createReverseProxyMock(ReverseProxyMethod.PORTPERREPO), false, ReverseProxyMethod.PORTPERREPO},
                {null, false, ReverseProxyMethod.SUBDOMAIN},
                {createReverseProxyMock(ReverseProxyMethod.SUBDOMAIN), true, ReverseProxyMethod.SUBDOMAIN},
                {createReverseProxyMock(ReverseProxyMethod.NOVALUE), true, ReverseProxyMethod.NOVALUE},
                {createReverseProxyMock(ReverseProxyMethod.REPOPATHPREFIX), true, ReverseProxyMethod.REPOPATHPREFIX},
                {createReverseProxyMock(ReverseProxyMethod.PORTPERREPO), true, ReverseProxyMethod.PORTPERREPO},
                {null, true, ReverseProxyMethod.REPOPATHPREFIX}
        };
    }

    private ReverseProxyDescriptor createReverseProxyMock(ReverseProxyMethod method) {
        ReverseProxyDescriptor reverseProxyDescriptor = mock(ReverseProxyDescriptor.class);
        when(reverseProxyDescriptor.getDockerReverseProxyMethod())
                .thenReturn(method);
        return reverseProxyDescriptor;
    }
}
