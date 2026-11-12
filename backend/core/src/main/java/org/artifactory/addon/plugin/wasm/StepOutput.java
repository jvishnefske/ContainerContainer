/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 * Modifications copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.wasm;

import java.util.Objects;

/** What running one step of a module produced: its stdout after a clean exit, or why there is none. */
public sealed interface StepOutput {

    /** {@code _start} returned or called {@code proc_exit(0)}, with this much stdout (at most the cap). */
    record Exited(byte[] stdout) implements StepOutput {
        public Exited {
            stdout = stdout.clone();
        }

        /** @return a copy of the output */
        @Override
        public byte[] stdout() {
            return stdout.clone();
        }
    }

    /** The step did not complete cleanly. */
    record Failed(Failure failure, String detail) implements StepOutput {
        public Failed {
            Objects.requireNonNull(failure, "failure");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /** Ways a step can fail; each fails the call closed (W4). */
    enum Failure {
        /** The module trapped (unreachable, out-of-bounds access, stack exhaustion, a Rust panic). */
        TRAP,
        /** The module exited with a non-zero code. */
        EXIT_CODE,
        /** The step passed its deadline and was interrupted. */
        TIMEOUT,
        /** The module wrote more than {@code maxStdoutBytes}. */
        STDOUT_OVERFLOW,
        /** The JVM ran out of memory running the step. */
        OUT_OF_MEMORY,
        /** No worker was free to run the step. */
        BUSY,
        /** The module could not be instantiated. */
        INSTANTIATION
    }
}
