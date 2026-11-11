package org.artifactory.api.request;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.mockito.invocation.InvocationOnMock;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
public class InternalArtifactoryRequestTest {
    private ArtifactoryHome artifactoryHome;

    @BeforeMethod
    public void init() {
        artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties artifactProperties = mock(ArtifactorySystemProperties.class);
        when(artifactProperties.getBooleanProperty(ConstantValues.internalRequestDisableMethodDelegation))
                .thenReturn(false);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactProperties);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryHome.unbind();
    }

    @Test
    public void testIsHeadOnly() {
        InternalArtifactoryRequest request = mock(InternalArtifactoryRequest.class, InvocationOnMock::callRealMethod);
        request.setRequestMethod("HEAD");
        Assert.assertTrue(request.isHeadOnly());

        request.setRequestMethod("GET");
        Assert.assertFalse(request.isHeadOnly());

        when(artifactoryHome.getArtifactoryProperties()
                .getBooleanProperty(ConstantValues.internalRequestDisableMethodDelegation)).thenReturn(true);
        request.setRequestMethod("HEAD");
        Assert.assertFalse(request.isHeadOnly());

        request.setRequestMethod("GET");
        Assert.assertFalse(request.isHeadOnly());

    }

    @Test
    public void testGetParameter() {
        InternalArtifactoryRequest request = mock(InternalArtifactoryRequest.class, InvocationOnMock::callRealMethod);
        request.setAlternateRemoteSiteUrl("");
        String parameter = request.getParameter("artifactory.alternativeRemoteSiteUrl");
        Assert.assertNotNull(parameter);
        Assert.assertEquals(parameter, "");
    }
}