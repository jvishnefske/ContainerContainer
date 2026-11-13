/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * The imperative shell of the initial-administrator bootstrap (IA1 to IA4): {@link AdminBootstrapService} applies
 * the pure rules of {@link org.artifactory.security.access.embedded.core.BootstrapRules} and
 * {@link org.artifactory.security.access.embedded.core.ClaimRateLimit} to the setup-token file, the environment and
 * the stored state, which it reaches through the narrow {@link BootstrapStatePort}. {@link PasswordHasher} turns
 * the chosen password into a slow salted hash.
 * <p>
 * Nothing here registers Spring beans: the embedded access factory constructs the service with a port over the
 * embedded store and publishes it in the application context, and the web layer looks it up by type.
 */
package org.artifactory.security.access.embedded.bootstrap;
