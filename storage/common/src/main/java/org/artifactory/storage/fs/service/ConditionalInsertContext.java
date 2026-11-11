package org.artifactory.storage.fs.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Thread-local holder for the conditional-insert flag.
 * Set by the move/copy entry point and read deep in the storage layer
 * (e.g. {@link FileService}) to decide between standard INSERT
 * and INSERT-IF-NOT-EXISTS.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConditionalInsertContext {

    private static final ThreadLocal<Boolean> ENABLED =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final ThreadLocal<Boolean> SKIPPED =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static void set(boolean enabled) {
        ENABLED.set(enabled);
    }

    public static boolean isEnabled() {
        return ENABLED.get();
    }

    public static void setSkipped(boolean skipped) {
        SKIPPED.set(skipped);
    }

    public static boolean wasSkipped() {
        return isEnabled() && SKIPPED.get();
    }

    public static void clear() {
        ENABLED.remove();
        SKIPPED.remove();
    }
}
