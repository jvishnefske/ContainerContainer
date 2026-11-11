package org.artifactory.federated.metadata;

import org.artifactory.common.config.NegotiationException;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Alexei Vainshtein
 */
@Test
public class NegotiationExceptionTest {

    @Test
    public void testConstructor() {
        NegotiationException exception = new NegotiationException("test");
        Assert.assertEquals(exception.getMessage(), "test");
    }
}