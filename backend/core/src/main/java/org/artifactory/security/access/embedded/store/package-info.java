/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * Persistence of the embedded Access implementation: the {@link
 * org.artifactory.security.access.embedded.store.EmbeddedAccessStore} contract, its JDBC implementation over the
 * Artifactory database ({@link org.artifactory.security.access.embedded.store.JdbcEmbeddedAccessStore}), the schema
 * bootstrap ({@link org.artifactory.security.access.embedded.store.EmbeddedAccessSchema}, Derby and PostgreSQL only)
 * and the post-commit change bus ({@link org.artifactory.security.access.embedded.store.EaChangeBus}).
 * <p>
 * This is the imperative shell around the pure rules in {@code embedded.core}: it owns the transactions, the clock
 * reads (through an injected {@link java.time.Clock}) and the delivery thread. No Spring is needed, so the store is
 * tested directly on in-memory Derby.
 */
package org.artifactory.security.access.embedded.store;
