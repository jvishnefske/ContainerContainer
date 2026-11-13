/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.artifactory.security.access.embedded.core.EaHashes;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.SigningKeys;
import org.artifactory.security.access.embedded.store.EaChange;
import org.artifactory.security.access.embedded.store.EaChangeBus;
import org.artifactory.security.access.embedded.store.EmbeddedAccessSchema;
import org.artifactory.security.access.embedded.store.JdbcEmbeddedAccessStore;
import org.jfrog.access.common.ServiceId;
import org.jfrog.storage.DbType;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Logger;

/** An embedded access client over a fresh in-memory Derby store, with a settable clock and a test hasher. */
final class EaFixture implements AutoCloseable {

    static final ServiceId RT = ServiceId.fromFormattedName("jfrt@01testinstance");
    static final String USER_SUBJECT_PREFIX = "jfrt@01testinstance/users/";

    private static SigningKeys keys;

    final MutableClock clock = new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    final EaChangeBus bus = new EaChangeBus();
    final BlockingQueue<EaChange> events = new LinkedBlockingQueue<>();
    final TestHasher hasher = new TestHasher();
    final JdbcEmbeddedAccessStore store;
    final EmbeddedAccessClient client;
    private final String url = "jdbc:derby:memory:ea-client-" + UUID.randomUUID();

    EaFixture() {
        bus.subscribe(events::add);
        DataSource ds = dataSource(url + ";create=true");
        ok(EmbeddedAccessSchema.ensure(ds, DbType.DERBY, clock));
        store = new JdbcEmbeddedAccessStore(ds, clock, bus);
        client = ok(EmbeddedAccessClient.builder().store(store).changeBus(bus).serviceId(RT)
                .signingKeys(signingKeys()).passwordVerifier(hasher).clock(clock).build());
    }

    static synchronized SigningKeys signingKeys() {
        if (keys == null) {
            keys = ok(EaKeyMaterial.generate(ServiceId.fromFormattedName("jfac@01testinstance"),
                    Clock.systemUTC()));
        }
        return keys;
    }

    static <T> T ok(EaOutcome<T> outcome) {
        if (outcome instanceof EaOutcome.Ok<T> ok) {
            return ok.value();
        }
        throw new AssertionError("expected Ok but was " + outcome);
    }

    /** Waits up to five seconds for an event matching a predicate. */
    EaChange awaitEvent(Predicate<EaChange> wanted) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            EaChange e = events.poll(100, TimeUnit.MILLISECONDS);
            if (e != null && wanted.test(e)) {
                return e;
            }
        }
        throw new AssertionError("no matching event");
    }

    @Override
    public void close() {
        bus.close();
        try {
            DriverManager.getConnection(url + ";drop=true").close();
        } catch (SQLException expected) {
            // Derby reports a successful drop as SQLState 08006.
        }
    }

    /** A clock the test moves. */
    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant value) {
            now = value;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** A hasher that counts comparisons, including dummy ones for a missing hash. */
    static final class TestHasher implements EaPasswordVerifier {
        final AtomicInteger comparisons = new AtomicInteger();
        final AtomicInteger dummyComparisons = new AtomicInteger();

        @Override
        public boolean matches(String raw, String hashOrNull) {
            comparisons.incrementAndGet();
            String candidate = encode(raw);
            if (hashOrNull == null) {
                dummyComparisons.incrementAndGet();
                EaHashes.constantTimeEquals(candidate.getBytes(), "{test}dummy".getBytes());
                return false;
            }
            return EaHashes.constantTimeEquals(candidate.getBytes(), hashOrNull.getBytes());
        }

        @Override
        public String encode(String raw) {
            return "{test}" + EaHashes.sha256Hex(raw);
        }
    }

    private static DataSource dataSource(String jdbcUrl) {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(jdbcUrl);
            }

            @Override
            public Connection getConnection(String u, String p) throws SQLException {
                return DriverManager.getConnection(jdbcUrl, u, p);
            }

            @Override
            public PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(PrintWriter out) {
            }

            @Override
            public void setLoginTimeout(int seconds) {
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public Logger getParentLogger() throws SQLFeatureNotSupportedException {
                throw new SQLFeatureNotSupportedException();
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                throw new SQLException("not a wrapper");
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }
        };
    }
}
