package org.artifactory.throttling.lh.mode;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Amir Avidan
 */
class ModeUpdateResultTest {

    private static final Instant SOME_INSTANT = Instant.now();

    @Test
    void changedReturnsTrueForUpdated() {
        assertTrue(new ModeUpdateResult(ModeUpdateStatus.UPDATED, SOME_INSTANT).changed());
    }

    @Test
    void changedReturnsTrueForExtended() {
        assertTrue(new ModeUpdateResult(ModeUpdateStatus.EXTENDED, SOME_INSTANT).changed());
    }

    @Test
    void changedReturnsFalseForAlreadyActive() {
        assertFalse(new ModeUpdateResult(ModeUpdateStatus.ALREADY_ACTIVE, SOME_INSTANT).changed());
    }

    @Test
    void changedReturnsFalseForUnchanged() {
        assertFalse(new ModeUpdateResult(ModeUpdateStatus.UNCHANGED, SOME_INSTANT).changed());
    }
}
