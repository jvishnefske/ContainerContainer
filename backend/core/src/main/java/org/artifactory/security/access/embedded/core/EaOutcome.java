/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The closed set of results of an embedded-access operation: {@link Ok} carries a value, {@link Refused} says the
 * operation was understood but not allowed or not valid (with a reason fit for a log or an error message), and
 * {@link Unsupported} says the embedded implementation deliberately does not provide the operation.
 * <p>
 * Values are never {@code null}: an operation with no meaningful result returns {@link #done()}, and an absent value
 * is expressed with {@link Optional}. The helpers {@link #map}, {@link #flatMap} and {@link #fold} pass a
 * {@code Refused} or {@code Unsupported} through unchanged.
 *
 * @param <T> the type of the value on success
 */
public sealed interface EaOutcome<T> {

    /** The unit value of an operation that succeeds without a result. */
    enum Done {
        /** The only instance. */
        DONE
    }

    /**
     * The operation succeeded.
     *
     * @param value the result, never {@code null}
     * @param <T>   the type of the result
     */
    record Ok<T>(T value) implements EaOutcome<T> {
        /** Rejects a {@code null} value. */
        public Ok {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * The operation was refused: the input is invalid or the rules forbid it.
     *
     * @param reason a human-readable reason, never {@code null}
     * @param <T>    the type the operation would have returned
     */
    record Refused<T>(String reason) implements EaOutcome<T> {
        /** Rejects a {@code null} reason. */
        public Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * The embedded implementation does not provide the operation.
     *
     * @param operation the name of the unsupported operation, never {@code null}
     * @param <T>       the type the operation would have returned
     */
    record Unsupported<T>(String operation) implements EaOutcome<T> {
        /** Rejects a {@code null} operation name. */
        public Unsupported {
            Objects.requireNonNull(operation, "operation");
        }
    }

    /**
     * Returns a successful outcome.
     *
     * @param value the result, never {@code null}
     * @param <T>   the type of the result
     * @return an {@link Ok}
     */
    static <T> EaOutcome<T> ok(T value) {
        return new Ok<>(value);
    }

    /**
     * Returns the successful outcome of an operation without a result.
     *
     * @return {@code Ok(DONE)}
     */
    static EaOutcome<Done> done() {
        return new Ok<>(Done.DONE);
    }

    /**
     * Returns a refused outcome.
     *
     * @param reason why the operation was refused
     * @param <T>    the type the operation would have returned
     * @return a {@link Refused}
     */
    static <T> EaOutcome<T> refused(String reason) {
        return new Refused<>(reason);
    }

    /**
     * Returns an unsupported outcome.
     *
     * @param operation the name of the operation
     * @param <T>       the type the operation would have returned
     * @return an {@link Unsupported}
     */
    static <T> EaOutcome<T> unsupported(String operation) {
        return new Unsupported<>(operation);
    }

    /**
     * Tells whether this is an {@link Ok}.
     *
     * @return {@code true} for {@code Ok}
     */
    default boolean isOk() {
        return this instanceof Ok;
    }

    /**
     * Returns the value of an {@link Ok}, or empty otherwise.
     *
     * @return the value if present
     */
    default Optional<T> toOptional() {
        return this instanceof Ok<T> ok ? Optional.of(ok.value()) : Optional.empty();
    }

    /**
     * Transforms the value of an {@link Ok}; other outcomes pass through.
     *
     * @param f   the transformation, which must not return {@code null}
     * @param <U> the new value type
     * @return the transformed outcome
     */
    default <U> EaOutcome<U> map(Function<? super T, ? extends U> f) {
        return flatMap(value -> ok(f.apply(value)));
    }

    /**
     * Chains an operation that itself returns an outcome; non-{@code Ok} outcomes pass through.
     *
     * @param f   the next operation
     * @param <U> the new value type
     * @return the outcome of {@code f}, or this outcome retyped
     */
    @SuppressWarnings("unchecked")
    default <U> EaOutcome<U> flatMap(Function<? super T, ? extends EaOutcome<? extends U>> f) {
        return switch (this) {
            case Ok<T> ok -> (EaOutcome<U>) f.apply(ok.value());
            case Refused<T> refused -> new Refused<>(refused.reason());
            case Unsupported<T> unsupported -> new Unsupported<>(unsupported.operation());
        };
    }

    /**
     * Reduces the outcome to a single value, handling every variant.
     *
     * @param onOk          applied to the value of an {@code Ok}
     * @param onRefused     applied to the reason of a {@code Refused}
     * @param onUnsupported applied to the operation of an {@code Unsupported}
     * @param <R>           the result type
     * @return the result of the matching function
     */
    default <R> R fold(Function<? super T, ? extends R> onOk, Function<String, ? extends R> onRefused,
            Function<String, ? extends R> onUnsupported) {
        return switch (this) {
            case Ok<T> ok -> onOk.apply(ok.value());
            case Refused<T> refused -> onRefused.apply(refused.reason());
            case Unsupported<T> unsupported -> onUnsupported.apply(unsupported.operation());
        };
    }
}
