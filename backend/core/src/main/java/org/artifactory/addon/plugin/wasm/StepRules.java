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

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The decision core of the step protocol (W2-W4). Pure: given the call, the state of the call so far and what the
 * last step produced, it says whether the call finished, needs effects performed, or fails.
 *
 * <p>Rules, all failing the call closed:
 * <ul>
 *     <li>a step that did not exit cleanly ({@link StepOutput.Failed}) fails the call;</li>
 *     <li>a malformed reply ({@link AbiCodec.Malformed}) fails it;</li>
 *     <li>a replacement {@code state} larger than {@code limits.maxStateBytes} fails it;</li>
 *     <li>asking for effects in the last allowed step ({@code limits.maxSteps}) fails it;</li>
 *     <li>any effect {@link EffectPolicy} denies fails it, and none of that step's effects is performed.</li>
 * </ul>
 * State seen by later steps of a call is the latest replacement a step of the call returned (or the plugin's state
 * when the call began); it is committed to the plugin only when the call finishes with a result.
 */
public final class StepRules {

    private StepRules() {
    }

    /**
     * Everything fixed for one call.
     *
     * @param request the hook's {@code request} value; not modified
     * @param state   the plugin state when the call began; empty for {@code null}
     * @param now     epoch seconds when the call began
     */
    public record CallSpec(Hook hook, JsonNode request, JsonText config, Optional<JsonText> state, long now,
            PluginLimits limits, Set<WasmManifest.AllowedHost> hosts) {
        public CallSpec {
            Objects.requireNonNull(hook, "hook");
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(limits, "limits");
            hosts = Set.copyOf(hosts);
        }
    }

    /**
     * The progress of a call before a step runs.
     *
     * @param step        the number of the step about to run, from 1
     * @param state       the state this step sees
     * @param replacement the latest replacement state a step of this call returned
     * @param effects     results of the previous step's effects, empty on step 1
     */
    public record CallState(int step, Optional<JsonText> state, Optional<JsonText> replacement,
            List<EffectResult> effects) {
        public CallState {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(replacement, "replacement");
            effects = List.copyOf(effects);
        }

        /** @return the state before step 1 of {@code spec} */
        public static CallState initial(CallSpec spec) {
            return new CallState(1, spec.state(), Optional.empty(), List.of());
        }

        /** @return the state for the next step, which sees {@code results} */
        public CallState withEffectResults(List<EffectResult> results) {
            return new CallState(step, state, replacement, results);
        }
    }

    /** What happens after a step. */
    public sealed interface Decision {
    }

    /**
     * The call finished.
     *
     * @param replacement the state to commit, if any step of the call replaced it
     */
    public record Finish(GuestResult result, Optional<JsonText> replacement) implements Decision {
        public Finish {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(replacement, "replacement");
        }
    }

    /**
     * Perform {@code effects} in order, then run the next step from {@code next} with their results.
     */
    public record Perform(List<Effect.Authorized> effects, CallState next) implements Decision {
        public Perform {
            effects = List.copyOf(effects);
            Objects.requireNonNull(next, "next");
        }
    }

    /** The call fails closed; nothing it asked for takes effect and no state is kept. */
    public record Fail(String reason) implements Decision {
        public Fail {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** @return the stdin of the step about to run */
    public static byte[] envelope(CallSpec spec, CallState state) {
        return AbiCodec.envelope(spec.hook(), spec.request(), spec.config(), state.state(), spec.now(),
                state.step(), state.effects());
    }

    /** Decides what follows the step {@code state.step()}, which produced {@code output}. */
    public static Decision decide(CallSpec spec, CallState state, StepOutput output) {
        if (output instanceof StepOutput.Failed failed) {
            return new Fail("step " + state.step() + " failed (" + failed.failure().name().toLowerCase(Locale.ROOT)
                    + "): " + failed.detail());
        }
        byte[] stdout = ((StepOutput.Exited) output).stdout();
        if (stdout.length > spec.limits().maxStdoutBytes()) {
            return new Fail("step " + state.step() + " wrote more than " + spec.limits().maxStdoutBytes() + " bytes");
        }
        AbiCodec.Reply reply = AbiCodec.reply(stdout, spec.hook());
        if (reply instanceof AbiCodec.Malformed malformed) {
            return new Fail("step " + state.step() + " reply is malformed: " + malformed.reason());
        }
        Optional<JsonText> newState = reply instanceof AbiCodec.Final done ? done.state()
                : ((AbiCodec.Ask) reply).state();
        if (newState.isPresent() && newState.get().utf8Length() > spec.limits().maxStateBytes()) {
            return new Fail("step " + state.step() + " state is larger than " + spec.limits().maxStateBytes()
                    + " bytes");
        }
        Optional<JsonText> replacement = newState.isPresent() ? newState : state.replacement();
        Optional<JsonText> seen = newState.isPresent() ? newState : state.state();
        if (reply instanceof AbiCodec.Final done) {
            return new Finish(done.result(), replacement);
        }
        AbiCodec.Ask ask = (AbiCodec.Ask) reply;
        if (state.step() >= spec.limits().maxSteps()) {
            return new Fail("step " + state.step() + " asks for effects but the call may take at most "
                    + spec.limits().maxSteps() + " steps");
        }
        List<Effect.Authorized> allowed = new ArrayList<>();
        for (Effect effect : ask.effects()) {
            switch (EffectPolicy.authorize(effect, spec.hook(), spec.hosts())) {
                case EffectPolicy.Allowed ok -> allowed.add(ok.effect());
                case EffectPolicy.Denied denied -> {
                    return new Fail("step " + state.step() + " asked for a disallowed effect: " + denied.reason());
                }
            }
        }
        return new Perform(allowed, new CallState(state.step() + 1, seen, replacement, List.of()));
    }
}
