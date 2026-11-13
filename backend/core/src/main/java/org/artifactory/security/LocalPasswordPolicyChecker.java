/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security;

import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * The {@link PasswordPolicyChecker} bean: the IA6 {@link PasswordPolicy} with the bundled common-password list
 * ({@link PasswordPolicy#withBundledList()}), loaded once. If the bundled list cannot be loaded every password is
 * refused (fail closed) and the reason is logged when the checker is created.
 */
@Component
public final class LocalPasswordPolicyChecker implements PasswordPolicyChecker {

    private static final Logger log = LoggerFactory.getLogger(LocalPasswordPolicyChecker.class);
    private static final String UNAVAILABLE = "the password policy is not available";

    private final EaOutcome<PasswordPolicy> policy;

    /** Creates the checker with the bundled list. */
    public LocalPasswordPolicyChecker() {
        this(PasswordPolicy.withBundledList());
    }

    /**
     * Creates the checker with an explicit policy outcome; used by tests.
     *
     * @param policy the policy, or a non-{@code Ok} outcome to refuse every password
     */
    public LocalPasswordPolicyChecker(EaOutcome<PasswordPolicy> policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
        if (!policy.isOk()) {
            String reason = policy.fold(value -> "ok", refused -> refused, op -> "unsupported: " + op);
            log.error("The password policy cannot be loaded; every new password is refused: {}", reason);
        }
    }

    @Override
    public Optional<String> violation(String username, String password) {
        Optional<PasswordPolicy> loaded = policy.toOptional();
        if (loaded.isEmpty()) {
            return Optional.of(UNAVAILABLE);
        }
        return loaded.get().validate(username, password)
                .fold(done -> Optional.<String>empty(), Optional::of, op -> Optional.of(UNAVAILABLE));
    }
}
