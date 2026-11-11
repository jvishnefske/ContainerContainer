package org.artifactory.io;

import org.artifactory.checksum.ChecksumsInfo;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
public class SimpleResourceStreamHandleTest {

    @Test
    public void testGetChecksumsInfo() {
        ChecksumsInfo info = new ChecksumsInfo();
        SimpleResourceStreamHandle simpleResourceStreamHandle = new SimpleResourceStreamHandle(null, 3, info);
        assertTrue(simpleResourceStreamHandle.getChecksumsInfo().isPresent());
        assertEquals(simpleResourceStreamHandle.getChecksumsInfo().get(), info);
    }

    @Test
    public void testGetNullChecksumsInfo() {
        SimpleResourceStreamHandle simpleResourceStreamHandle = new SimpleResourceStreamHandle(null, 3, null);
        assertFalse(simpleResourceStreamHandle.getChecksumsInfo().isPresent());
    }
}