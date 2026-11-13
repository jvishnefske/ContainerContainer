/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * Embedded access: the in-process replacement for JFrog Access, enabled by
 * {@code artifactory.access.embedded.enabled} (default {@code true}).
 * <p>
 * Sub-packages: {@code core} (pure rules and records), {@code store} (the {@code ea_*} tables on Derby or
 * PostgreSQL), {@code client} (the {@code AccessClient} implementation) and {@code bootstrap} (the initial
 * administrator, IA1 to IA8). This package wires them for a running Artifactory:
 * {@link org.artifactory.security.access.embedded.EmbeddedAccessFactory} builds one
 * {@link org.artifactory.security.access.embedded.EmbeddedAccess} from Artifactory's data source and master key,
 * with the signing key kept encrypted by
 * {@link org.artifactory.security.access.embedded.SigningKeyVault}, the bootstrap state behind
 * {@link org.artifactory.security.access.embedded.StoreBootstrapStatePort} and expired tokens deleted by
 * {@link org.artifactory.security.access.embedded.EaTokenPurgeJob}. {@code AccessServiceImpl} calls the factory
 * from {@code bootstrapAccessClient()}, installs the client, writes its certificate as the Access root
 * certificate and publishes the bootstrap service in the application context.
 * <p>
 * {@code ea_kv} keys used here: {@code signing.key} (encrypted signing key and certificate),
 * {@code bootstrap.state} (initial-admin state); the store and client also use {@code permissions.last_updated}
 * and the configuration documents of the client package.
 */
package org.artifactory.security.access.embedded;
