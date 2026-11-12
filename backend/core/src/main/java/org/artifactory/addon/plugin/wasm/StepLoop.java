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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Drives one call through the step protocol: build the envelope, run the step, let {@link StepRules} decide, perform
 * the effects it allows, repeat. The side effects are behind {@link Runner} (running the module) and
 * {@link Performer} (performing effects), so the loop itself is deterministic and is tested with fakes.
 *
 * <p>The loop runs at most {@code limits.maxSteps} steps. An envelope larger than {@code limits.maxStdinBytes} fails
 * the call before the step runs. The loop never throws: a runner or performer that throws despite its contract
 * fails the call.
 */
public final class StepLoop {

    private StepLoop() {
    }

    /** Runs one step: a fresh instance of the module with {@code stdin}. */
    @FunctionalInterface
    public interface Runner {
        /** Never throws; every problem is a {@link StepOutput.Failed}. */
        StepOutput run(byte[] stdin);
    }

    /** Performs one allowed effect. */
    @FunctionalInterface
    public interface Performer {
        /** Never throws; a failure the module may see is a result with {@code ok: false}. */
        EffectResult perform(Effect.Authorized effect);
    }

    /** The outcome of a call. */
    public sealed interface Outcome {
    }

    /** The call finished with {@code result}; {@code replacement} is the state to commit, if any. */
    public record Completed(GuestResult result, Optional<JsonText> replacement) implements Outcome {
        public Completed {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(replacement, "replacement");
        }
    }

    /** The call failed closed. */
    public record Failed(String reason) implements Outcome {
        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Runs the call {@code spec}. */
    public static Outcome run(StepRules.CallSpec spec, Runner runner, Performer performer) {
        StepRules.CallState state = StepRules.CallState.initial(spec);
        for (int i = 0; i < spec.limits().maxSteps(); i++) {
            byte[] stdin = StepRules.envelope(spec, state);
            if (stdin.length > spec.limits().maxStdinBytes()) {
                return new Failed("step " + state.step() + " envelope is larger than "
                        + spec.limits().maxStdinBytes() + " bytes");
            }
            StepOutput output;
            try {
                output = runner.run(stdin);
            } catch (RuntimeException e) {
                output = new StepOutput.Failed(StepOutput.Failure.TRAP, String.valueOf(e));
            }
            StepRules.Decision decision = StepRules.decide(spec, state, output);
            switch (decision) {
                case StepRules.Finish finish -> {
                    return new Completed(finish.result(), finish.replacement());
                }
                case StepRules.Fail fail -> {
                    return new Failed(fail.reason());
                }
                case StepRules.Perform perform -> {
                    List<EffectResult> results = new ArrayList<>();
                    for (Effect.Authorized effect : perform.effects()) {
                        try {
                            results.add(performer.perform(effect));
                        } catch (RuntimeException e) {
                            return new Failed("performing an effect failed: " + e.getClass().getSimpleName());
                        }
                    }
                    state = perform.next().withEffectResults(results);
                }
            }
        }
        return new Failed("the call took more than " + spec.limits().maxSteps() + " steps");
    }
}
