package org.artifactory.request;

import org.artifactory.api.request.ArtifactoryResponseBase;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.testng.annotations.Test;

import static org.artifactory.api.request.ArtifactoryHeaders.THIRD_PARTY_ORIGIN;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
public class ArtifactoryResponseBaseTest {

    @Test
    public void testChecksumDeployed() {
        ArtifactoryResponseBase responseBase =
                Mockito.mock(ArtifactoryResponseBase.class, InvocationOnMock::callRealMethod);
        assertFalse(responseBase.isChecksumDeployed());
        responseBase.checksumDeployed();
        assertTrue(responseBase.isChecksumDeployed());
    }

    @Test
    public void testThirdPartyOriginHeader() {
        ArtifactoryResponseBase responseBase = spy(ArtifactoryResponseBase.class);
        responseBase.setThirdPartyOriginHeader("value");
        verify(responseBase).setHeader(THIRD_PARTY_ORIGIN, "value");
    }
}