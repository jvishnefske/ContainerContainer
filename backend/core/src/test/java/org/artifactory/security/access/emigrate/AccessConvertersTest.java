package org.artifactory.security.access.emigrate;

import org.testng.annotations.Test;

import java.util.Collections;

import static org.testng.Assert.assertEquals;

/**
 * @author Omri Ziv
 */
public class AccessConvertersTest {

    private final AccessConverters accessConverters = new AccessConverters();

    @Test
    public void testGet() {
        assertEquals(Collections.emptyList(), accessConverters.getAll());
    }
}
