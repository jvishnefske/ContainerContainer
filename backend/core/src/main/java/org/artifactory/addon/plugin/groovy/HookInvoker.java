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
import org.artifactory.exception.CancelException;
import org.codehaus.groovy.runtime.InvokerInvocationException;

import java.util.Arrays;
import java.util.Optional;

/**
 * Runs plugin closures and classifies the result as a {@link HookOutcome}.
 *
 * <p>Each call works on a clone of the closure, so the delegate it sets (the call site's context object,
 * for example a {@code ResponseCtx} whose {@code status} an {@code altResponse} hook assigns) is private to
 * that call. The delegate is resolved first, then the script, which holds the {@code log},
 * {@code repositories} and {@code security} bindings.
 *
 * <p>Like the Pro engine, a closure may declare fewer parameters than the call site passes; the extra
 * trailing arguments are dropped (see {@link #fitArgs(Object[], int)}).
 */
public final class HookInvoker {

    private HookInvoker() {
    }

    /**
     * Calls {@code closure} with {@code delegate} and {@code args}. Exceptions, assertion and linkage errors
     * and stack overflows are classified; other virtual machine errors such as {@code OutOfMemoryError}
     * propagate.
     *
     * @param delegate object the closure body resolves names against first, or {@code null} for none
     */
    public static HookOutcome invoke(Closure<?> closure, Object delegate, Object... args) {
        try {
            Closure<?> call = (Closure<?>) closure.clone();
            if (delegate != null) {
                call.setDelegate(delegate);
                call.setResolveStrategy(Closure.DELEGATE_FIRST);
            }
            Object[] safeArgs = args == null ? new Object[0] : args;
            return new HookOutcome.Completed(call.call(fitArgs(safeArgs, call.getMaximumNumberOfParameters())));
        } catch (Exception | LinkageError | AssertionError | StackOverflowError t) {
            Optional<CancelException> cancel = findCancel(t);
            if (cancel.isPresent()) {
                return new HookOutcome.Cancelled(cancel.get());
            }
            return new HookOutcome.Failed(t);
        }
    }

    /**
     * Truncates {@code args} to the number of parameters a closure accepts.
     *
     * @param maxParams the closure's {@code getMaximumNumberOfParameters()}
     * @return {@code args} itself when it already fits, otherwise its leading {@code maxParams} elements
     */
    public static Object[] fitArgs(Object[] args, int maxParams) {
        if (args.length <= maxParams) {
            return args;
        }
        return Arrays.copyOf(args, Math.max(0, maxParams));
    }

    /**
     * Finds a {@link CancelException} in a throwable's cause chain; Groovy may wrap exceptions thrown from
     * closures. The walk is bounded so a cyclic cause chain cannot loop forever.
     */
    public static Optional<CancelException> findCancel(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof CancelException cancel) {
                return Optional.of(cancel);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    /** @return a one-line description of a failure, unwrapping Groovy's invocation wrappers */
    public static String describe(Throwable error) {
        Throwable root = error;
        for (int depth = 0; root instanceof InvokerInvocationException && root.getCause() != null && depth < 16;
                depth++) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }
}
