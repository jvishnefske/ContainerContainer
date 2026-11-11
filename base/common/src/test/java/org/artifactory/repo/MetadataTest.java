package org.artifactory.repo;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;


/**
 * @author Alexei Vainshtein
 */
public class MetadataTest {

    @Test
    public void testMetadataFields() {
        Metadata metadata = new Metadata("{}", true);
        assertEquals(metadata.packageMetadata(), "{}");
        assertTrue(metadata.negotiationRequired());
    }
}