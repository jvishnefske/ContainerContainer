/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * The pure core of embedded access, the fork's in-process replacement for JFrog Access: immutable value types
 * ({@link EaUser}, {@link EaGroup}, {@link EaPermission}, {@link EaToken}, {@link SigningKeys}), the result type
 * {@link EaOutcome}, and the rules ({@link ScopeGrammar}, {@link Revocability}, {@link TokenClaimsFactory},
 * {@link PasswordPolicy}, {@link BootstrapRules}, {@link ClaimRateLimit}, {@link LastUpdatedClock}).
 * <p>
 * Nothing here uses Spring, JDBC, the network or the system clock: time is always passed in. The only I/O is
 * {@link PasswordPolicy#withBundledList()}, which reads a classpath resource. Failures are returned as
 * {@code EaOutcome.Refused}; constructors of value types throw only on programming errors such as {@code null}
 * required fields.
 */
package org.artifactory.security.access.embedded.core;
