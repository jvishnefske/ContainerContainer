package org.artifactory.rest.resource.mfe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MfeNameTest {

    @Test
    void fromValue_shouldReturnEnum_whenValueMatches() {
        MfeName result = MfeName.fromValue("core");

        assertEquals(MfeName.CORE, result);
    }

    @Test
    void fromValue_shouldReturnNull_whenValueUnknown() {
        MfeName result = MfeName.fromValue("unknown");

        assertNull(result);
    }

    @Test
    void toString_shouldReturnValue_whenCalled() {
        assertEquals("rtui", MfeName.RTUI.toString());
    }
}
