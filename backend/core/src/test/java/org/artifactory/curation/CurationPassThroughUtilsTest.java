package org.artifactory.curation;

import org.artifactory.api.request.ArtifactoryHeaders;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurationPassThroughUtilsTest {

    @Test
    void isPassThroughHeaderPresent_whenHeaderTrue_returnsTrue() {
        assertTrue(CurationPassThroughUtils.isPassThroughHeaderPresent(
                Map.of(ArtifactoryHeaders.PASS_THROUGH_KEY, "true")));
    }

    @Test
    void isPassThroughHeaderPresent_whenHeaderMissing_returnsFalse() {
        assertFalse(CurationPassThroughUtils.isPassThroughHeaderPresent(Map.of()));
        assertFalse(CurationPassThroughUtils.isPassThroughHeaderPresent(null));
    }
}
