/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.store;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EaChangeBusTest {

    private static EaChange revoked(int i) {
        return new EaChange.TokenRevoked("s" + i);
    }

    @Test
    void deliversInOrderToEveryListenerAndSurvivesListenerFailures() throws InterruptedException {
        LinkedBlockingQueue<EaChange> received = new LinkedBlockingQueue<>();
        try (EaChangeBus bus = new EaChangeBus()) {
            bus.subscribe(change -> {
                throw new IllegalStateException("listener bug");
            });
            bus.subscribe(received::add);
            for (int i = 0; i < 10; i++) {
                bus.publish(revoked(i));
            }
            for (int i = 0; i < 10; i++) {
                assertEquals(revoked(i), received.poll(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void overflowCoalescesIntoOneEverythingChangedEvent() throws InterruptedException {
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        LinkedBlockingQueue<EaChange> received = new LinkedBlockingQueue<>();
        int capacity = 4;
        try (EaChangeBus bus = new EaChangeBus(capacity)) {
            bus.subscribe(change -> {
                if (change.equals(revoked(0))) {
                    blocked.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                received.add(change);
            });
            bus.publish(revoked(0));
            assertTrue(blocked.await(5, TimeUnit.SECONDS));
            for (int i = 1; i <= capacity + 10; i++) {
                bus.publish(revoked(i));
            }
            release.countDown();
            assertEquals(revoked(0), received.poll(5, TimeUnit.SECONDS));
            assertEquals(new EaChange.EverythingChanged(), received.poll(5, TimeUnit.SECONDS));
            bus.publish(revoked(99));
            assertEquals(revoked(99), received.poll(5, TimeUnit.SECONDS), "normal delivery resumes");
            assertNull(received.poll(100, TimeUnit.MILLISECONDS), "the queued events were coalesced away");
        }
    }

    @Test
    void publishAfterCloseIsIgnoredAndUnsubscribeStopsDelivery() throws InterruptedException {
        List<EaChange> received = new ArrayList<>();
        LinkedBlockingQueue<EaChange> other = new LinkedBlockingQueue<>();
        EaChangeBus bus = new EaChangeBus();
        EaChangeBus.Listener listener = received::add;
        bus.subscribe(listener);
        bus.subscribe(other::add);
        bus.unsubscribe(listener);
        bus.publish(new EaChange.PermissionsAffected(Set.of("a"), Set.of()));
        assertEquals(new EaChange.PermissionsAffected(Set.of("a"), Set.of()), other.poll(5, TimeUnit.SECONDS));
        bus.close();
        bus.publish(revoked(1));
        assertTrue(received.isEmpty());
        assertNull(other.poll(100, TimeUnit.MILLISECONDS));
    }
}
