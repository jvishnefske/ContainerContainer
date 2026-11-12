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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.addon.plugin.groovy.HostedOutcome;
import org.artifactory.addon.plugin.groovy.RealmResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** W2-W4: the step loop with a scripted module and a recording effect performer. */
class StepLoopTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<WasmManifest.AllowedHost> HOSTS = Set.of(new WasmManifest.AllowedHost("a.test", 443));

    private final List<JsonNode> envelopes = new ArrayList<>();
    private final List<Effect.Authorized> performed = new ArrayList<>();

    /** A module that answers each step with the next scripted output and records the envelopes. */
    private StepLoop.Runner script(StepOutput... outputs) {
        Deque<StepOutput> queue = new ArrayDeque<>(List.of(outputs));
        return stdin -> {
            try {
                envelopes.add(JSON.readTree(stdin));
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            return queue.isEmpty() ? new StepOutput.Failed(StepOutput.Failure.TRAP, "script ended") : queue.pop();
        };
    }

    private static StepOutput out(String json) {
        return new StepOutput.Exited(json.getBytes(StandardCharsets.UTF_8));
    }

    private final StepLoop.Performer performer = effect -> {
        performed.add(effect);
        return effect instanceof Effect.AllowedGet
                ? new EffectResult.HttpGet(true, 200, Map.of(), "allow".getBytes(), "")
                : new EffectResult.Mint(true, "tok", 60, "");
    };

    private static StepRules.CallSpec spec(Hook hook, PluginLimits limits) {
        return new StepRules.CallSpec(hook, AbiCodec.realmBearer("r", "t"), new JsonText("{\"k\":1}"),
                Optional.of(new JsonText("{\"calls\":1}")), 777, limits, HOSTS);
    }

    private static PluginLimits limits(Map<PluginLimits.Limit, Integer> values) {
        return PluginLimits.of(values);
    }

    private static String failure(StepLoop.Outcome outcome) {
        return assertInstanceOf(StepLoop.Failed.class, outcome).reason();
    }

    @Test
    void effectsRoundTripIntoTheNextStepAndTheLatestStateIsCommitted() {
        StepLoop.Outcome outcome = StepLoop.run(spec(Hook.EXECUTION, PluginLimits.defaults()), script(
                out("{\"abi\":1,\"effects\":[{\"type\":\"http_get\",\"url\":\"https://a.test/k\"},"
                        + "{\"type\":\"mint_access_token\",\"expires_in\":60}],\"state\":{\"calls\":2}}"),
                out("{\"abi\":1,\"result\":{\"status\":200}}")), performer);
        StepLoop.Completed done = assertInstanceOf(StepLoop.Completed.class, outcome);
        assertEquals(Optional.of(new JsonText("{\"calls\":2}")), done.replacement());
        assertEquals(2, performed.size());
        assertEquals(2, envelopes.size());
        JsonNode first = envelopes.get(0);
        assertEquals(1, first.get("step").asInt());
        assertEquals(0, first.get("effects").size());
        assertEquals(1, first.at("/state/calls").asInt());
        JsonNode second = envelopes.get(1);
        assertEquals(2, second.get("step").asInt());
        assertEquals(777, second.get("now").asLong());
        assertEquals("YWxsb3c=", second.at("/effects/0/body").asText());
        assertEquals("tok", second.at("/effects/1/access_token").asText());
        assertEquals(2, second.at("/state/calls").asInt(), "later steps see the call's latest state");
        assertEquals(1, second.at("/config/k").asInt());
    }

    @Test
    void aResultWithoutStateKeepsTheCurrentOne() {
        StepLoop.Completed done = assertInstanceOf(StepLoop.Completed.class, StepLoop.run(
                spec(Hook.REALM_BEARER, PluginLimits.defaults()),
                script(out("{\"abi\":1,\"result\":{\"outcome\":\"not_mine\"}}")), performer));
        assertEquals(Optional.empty(), done.replacement());
        assertInstanceOf(RealmResult.NotMine.class, ((GuestResult.Realm) done.result()).result());
    }

    @Test
    void theStepCapEndsTheCall() {
        String ask = "{\"abi\":1,\"effects\":[{\"type\":\"http_get\",\"url\":\"https://a.test/\"}],\"state\":1}";
        StepLoop.Outcome outcome = StepLoop.run(spec(Hook.REALM_BEARER,
                limits(Map.of(PluginLimits.Limit.MAX_STEPS, 3))), script(out(ask), out(ask), out(ask), out(ask)),
                performer);
        assertTrue(failure(outcome).contains("at most 3 steps"), failure(outcome));
        assertEquals(3, envelopes.size());
        assertEquals(2, performed.size(), "the third step's effects are not performed");
    }

    @Test
    void malformedRepliesAndFailedStepsFailClosedWithoutState() {
        for (StepOutput bad : List.of(out("{\"abi\":1,\"result\":{\"outcome\":\"maybe\"},\"state\":9}"),
                out("garbage"),
                new StepOutput.Failed(StepOutput.Failure.TRAP, "unreachable"),
                new StepOutput.Failed(StepOutput.Failure.EXIT_CODE, "exit code 1"),
                new StepOutput.Failed(StepOutput.Failure.TIMEOUT, "deadline"),
                new StepOutput.Failed(StepOutput.Failure.STDOUT_OVERFLOW, "too much"),
                new StepOutput.Failed(StepOutput.Failure.OUT_OF_MEMORY, "oom"))) {
            StepLoop.Outcome outcome = StepLoop.run(spec(Hook.REALM_BEARER, PluginLimits.defaults()),
                    script(out("{\"abi\":1,\"effects\":[{\"type\":\"http_get\",\"url\":\"https://a.test/\"}],"
                            + "\"state\":{\"x\":1}}"), bad), performer);
            assertInstanceOf(StepLoop.Failed.class, outcome, String.valueOf(bad));
        }
    }

    @Test
    void oversizedOutputStateAndEnvelopeFail() {
        PluginLimits small = limits(Map.of(PluginLimits.Limit.MAX_STDOUT_BYTES, 1024,
                PluginLimits.Limit.MAX_STATE_BYTES, 10, PluginLimits.Limit.MAX_STDIN_BYTES, 1024));
        String padded = "{\"abi\":1,\"result\":{\"outcome\":\"not_mine\"},\"pad\":\"" + "x".repeat(1100) + "\"}";
        assertTrue(failure(StepLoop.run(spec(Hook.REALM_BEARER, small), script(out(padded)), performer))
                .contains("more than 1024 bytes"));
        assertTrue(failure(StepLoop.run(spec(Hook.REALM_BEARER, small), script(out(
                "{\"abi\":1,\"result\":{\"outcome\":\"not_mine\"},\"state\":\"01234567890\"}")), performer))
                .contains("state is larger"));
        StepRules.CallSpec big = new StepRules.CallSpec(Hook.REALM_BEARER, AbiCodec.realmBearer("r",
                "t".repeat(2000)), JsonText.NULL, Optional.empty(), 1, small, HOSTS);
        int stepsRun = envelopes.size();
        assertTrue(failure(StepLoop.run(big, script(), performer)).contains("envelope is larger"));
        assertEquals(stepsRun, envelopes.size(), "an oversized envelope is never handed to the module");
    }

    @Test
    void aDisallowedEffectFailsTheCallAndNothingIsPerformed() {
        String mixed = "{\"abi\":1,\"effects\":[{\"type\":\"http_get\",\"url\":\"https://a.test/\"},"
                + "{\"type\":\"http_get\",\"url\":\"https://elsewhere.test/\"}]}";
        assertTrue(failure(StepLoop.run(spec(Hook.REALM_BEARER, PluginLimits.defaults()), script(out(mixed)),
                performer)).contains("disallowed effect"));
        String mint = "{\"abi\":1,\"effects\":[{\"type\":\"mint_access_token\",\"expires_in\":60}]}";
        assertTrue(failure(StepLoop.run(spec(Hook.REALM_BEARER, PluginLimits.defaults()), script(out(mint)),
                performer)).contains("execution calls only"));
        assertEquals(List.of(), performed);
    }

    @Test
    void stateIsCommittedOnlyBySuccessfulCalls() {
        WasmManifest manifest = new WasmManifest(List.of(new WasmManifest.RealmDecl("r", false,
                Set.of(WasmManifest.RealmHookKind.BEARER))), List.of(), HOSTS, PluginLimits.defaults(),
                JsonText.NULL);
        Deque<StepOutput> outputs = new ArrayDeque<>(List.of(
                out("{\"abi\":1,\"result\":{\"outcome\":\"not_mine\"},\"state\":{\"v\":1}}"),
                out("{\"abi\":1,\"effects\":[{\"type\":\"http_get\",\"url\":\"https://a.test/\"}],\"state\":{\"v\":2}}"),
                new StepOutput.Failed(StepOutput.Failure.TRAP, "boom"),
                out("{\"abi\":1,\"result\":{\"outcome\":\"bogus\"},\"state\":{\"v\":3}}")));
        WasmPlugin plugin = new WasmPlugin("p", manifest, stdin -> outputs.pop(), performer,
                WasmPlugin.RequestContext.NONE, () -> 1);
        var realm = plugin.toLoadedPlugin("p.wasm").realms().get(0);
        var bearer = (org.artifactory.addon.plugin.groovy.RealmHook.Hosted) realm.bearer().orElseThrow();
        assertInstanceOf(HostedOutcome.Answered.class, bearer.call().call(List.of("t")));
        assertEquals(Optional.of(new JsonText("{\"v\":1}")), plugin.state().current());
        assertInstanceOf(HostedOutcome.Failed.class, bearer.call().call(List.of("t")));
        assertEquals(Optional.of(new JsonText("{\"v\":1}")), plugin.state().current(), "trap keeps no state");
        assertInstanceOf(HostedOutcome.Failed.class, bearer.call().call(List.of("t")));
        assertEquals(Optional.of(new JsonText("{\"v\":1}")), plugin.state().current(), "malformed keeps no state");
    }
}
