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

package org.artifactory.addon.plugin.groovy;

import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs realm closures and turns whatever happens into a {@link RealmResult}, failing closed.
 *
 * <p>A closure that returns a {@link RealmResult} gives that result. A closure that returns anything else, throws
 * (including {@code CancelException}) or overflows its stack gives {@link RealmResult.NotMine}, and the problem is
 * reported with the plugin and realm name, so a broken realm can never authenticate anyone (P1). Closures run in
 * the {@link PluginCallScope.Kind#REALM} scope with no delegate: names resolve against the script, whose binding
 * holds {@code realm}, {@code security}, {@code log} and the other variables.
 *
 * <p>Hosted hooks ({@link RealmHook.Hosted}, for example a WASM module's) run in the same scope. Their
 * {@link HostedOutcome.Failed} answer, and anything they throw despite their contract, also gives
 * {@link RealmResult.NotMine} and is reported the same way.
 */
public final class RealmInvoker {

    private RealmInvoker() {
    }

    /**
     * Calls a realm's {@code authenticate} or {@code bearer} hook, whichever kind it is.
     *
     * @param what which hook, for example {@code realms.corp.bearer}, used in reports
     * @param args the credentials: user name and password, or the token
     */
    public static RealmResult result(PluginRealm realm, RealmHook hook, String what, HookFailureReporter reporter,
            Object... args) {
        return switch (hook) {
            case RealmHook.Script script -> result(realm, script.closure(), what, reporter, args);
            case RealmHook.Hosted hosted -> hosted(realm, hosted.call(), what, reporter, args);
        };
    }

    private static RealmResult hosted(PluginRealm realm, RealmHook.Call call, String what,
            HookFailureReporter reporter, Object... args) {
        List<String> credentials = new ArrayList<>(args.length);
        for (Object arg : args) {
            credentials.add(arg == null ? null : String.valueOf(arg));
        }
        HostedOutcome<RealmResult> outcome;
        try {
            outcome = PluginCallScope.run(PluginCallScope.Kind.REALM,
                    () -> call.call(Collections.unmodifiableList(credentials)));
        } catch (RuntimeException e) {
            reporter.report(realm.pluginName(), what, e);
            return new RealmResult.NotMine();
        }
        return switch (outcome) {
            case HostedOutcome.Answered<RealmResult> answered -> answered.value();
            case HostedOutcome.Failed<RealmResult> failed -> {
                reporter.report(realm.pluginName(), what, new IllegalStateException(failed.reason()));
                yield new RealmResult.NotMine();
            }
        };
    }

    /**
     * Calls a realm's {@code authenticate} or {@code bearer} closure.
     *
     * @param what which closure, for example {@code realms.corp.bearer}, used in reports
     */
    public static RealmResult result(PluginRealm realm, Closure<?> closure, String what, HookFailureReporter reporter,
            Object... args) {
        HookOutcome outcome = PluginCallScope.run(PluginCallScope.Kind.REALM,
                () -> HookInvoker.invoke(closure, null, args));
        return switch (outcome) {
            case HookOutcome.Completed completed when completed.value() instanceof RealmResult result -> result;
            case HookOutcome.Completed completed -> {
                reporter.report(realm.pluginName(), what, new IllegalStateException(
                        "Realm closure returned " + describe(completed.value())
                                + " instead of realm.authenticated(...), realm.notMine() or realm.rejected(...)"));
                yield new RealmResult.NotMine();
            }
            case HookOutcome.Failed failed -> {
                reporter.report(realm.pluginName(), what, failed.error());
                yield new RealmResult.NotMine();
            }
            case HookOutcome.Cancelled cancelled -> {
                reporter.report(realm.pluginName(), what, cancelled.cancel());
                yield new RealmResult.NotMine();
            }
        };
    }

    /**
     * Calls a realm's {@code userExists} closure.
     *
     * @return true only when the closure returned {@code Boolean.TRUE}; anything else, or a failure, is false
     */
    public static boolean userExists(PluginRealm realm, Closure<?> closure, HookFailureReporter reporter,
            String username) {
        HookOutcome outcome = PluginCallScope.run(PluginCallScope.Kind.REALM,
                () -> HookInvoker.invoke(closure, null, username));
        return switch (outcome) {
            case HookOutcome.Completed completed -> Boolean.TRUE.equals(completed.value());
            case HookOutcome.Failed failed -> {
                reporter.report(realm.pluginName(), "realms." + realm.name() + ".userExists", failed.error());
                yield false;
            }
            case HookOutcome.Cancelled cancelled -> {
                reporter.report(realm.pluginName(), "realms." + realm.name() + ".userExists", cancelled.cancel());
                yield false;
            }
        };
    }

    private static String describe(Object value) {
        return value == null ? "null" : "a " + value.getClass().getName();
    }
}
