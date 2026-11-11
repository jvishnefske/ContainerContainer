/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.concurrent;

import org.jfrog.common.concurrency.ConflictsGuard;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConflictsGuardUtilsTest {

    @Test
    void runWithLocks_shouldExecuteAction_whenLocksAcquired() throws InterruptedException {
        // Arrange
        @SuppressWarnings("unchecked")
        ConflictsGuard<String> guard = mock(ConflictsGuard.class);
        when(guard.tryToLock(any(), anyLong(), any())).thenReturn(true);
        AtomicBoolean actionExecuted = new AtomicBoolean(false);

        // Act
        ConflictsGuardUtils.runWithLocks(guard, Set.of("key1", "key2"), 1, TimeUnit.SECONDS,
                () -> actionExecuted.set(true));

        // Assert
        assertTrue(actionExecuted.get());
        verify(guard, times(2)).tryToLock(any(), anyLong(), any());
        verify(guard, times(2)).unlock(any());
    }

    @Test
    void runWithLocks_shouldSkipExecuteActionWithoutLocking_whenKeysAreNull() throws InterruptedException {
        // Arrange
        @SuppressWarnings("unchecked")
        ConflictsGuard<String> guard = mock(ConflictsGuard.class);
        AtomicBoolean actionExecuted = new AtomicBoolean(false);

        // Act
        ConflictsGuardUtils.runWithLocks(guard, null, 1, TimeUnit.SECONDS, () -> actionExecuted.set(true));

        // Assert
        assertFalse(actionExecuted.get());
        verify(guard, never()).tryToLock(any(), anyLong(), any());
        verify(guard, never()).unlock(any());
    }

    @Test
    void runWithLocks_shouldSkipExecuteActionWithoutLocking_whenKeysAreEmpty() throws InterruptedException {
        // Arrange
        @SuppressWarnings("unchecked")
        ConflictsGuard<String> guard = mock(ConflictsGuard.class);
        AtomicBoolean actionExecuted = new AtomicBoolean(false);

        // Act
        ConflictsGuardUtils.runWithLocks(guard, Collections.emptySet(), 1, TimeUnit.SECONDS,
                () -> actionExecuted.set(true));

        // Assert
        assertFalse(actionExecuted.get());
        verify(guard, never()).tryToLock(any(), anyLong(), any());
        verify(guard, never()).unlock(any());
    }

    @Test
    void runWithLocks_shouldThrowLockAlreadyTakenException_whenLockAcquisitionFails() throws InterruptedException {
        // Arrange
        @SuppressWarnings("unchecked")
        ConflictsGuard<String> guard = mock(ConflictsGuard.class);
        when(guard.tryToLock(any(), anyLong(), any())).thenReturn(false);
        AtomicBoolean actionExecuted = new AtomicBoolean(false);

        // Act & Assert
        Set<String> keys = Set.of("key1");
        LockAlreadyTakenException exception = assertThrows(LockAlreadyTakenException.class,
                () -> ConflictsGuardUtils.runWithLocks(guard, keys, 1, TimeUnit.SECONDS,
                        () -> actionExecuted.set(true)));

        assertTrue(exception.getMessage().contains("key1"));
        assertFalse(actionExecuted.get());
        verify(guard, never()).unlock(any());
    }

    @Test
    void runWithLocks_shouldThrowLockingFailedException_whenInterrupted() throws InterruptedException {
        // Arrange
        @SuppressWarnings("unchecked")
        ConflictsGuard<String> guard = mock(ConflictsGuard.class);
        when(guard.tryToLock(any(), anyLong(), any())).thenThrow(new InterruptedException("test interrupt"));
        AtomicBoolean actionExecuted = new AtomicBoolean(false);

        // Act & Assert
        Set<String> keys = Set.of("key1");
        LockingFailedException exception = assertThrows(LockingFailedException.class,
                () -> ConflictsGuardUtils.runWithLocks(guard, keys, 1, TimeUnit.SECONDS,
                        () -> actionExecuted.set(true)));

        assertTrue(exception.getMessage().contains("Interrupted"));
        assertInstanceOf(InterruptedException.class, exception.getCause());
        assertFalse(actionExecuted.get());
        assertTrue(Thread.currentThread().isInterrupted());
        // Clear the interrupt flag for subsequent tests
        Thread.interrupted();
    }

    @Test
    void runWithLocks_shouldReleaseAcquiredLocks_whenLaterLockFails() throws InterruptedException {
        // Arrange
        @SuppressWarnings("unchecked")
        ConflictsGuard<String> guard = mock(ConflictsGuard.class);
        // Use LinkedHashSet to ensure predictable iteration order
        Set<String> keys = new LinkedHashSet<>();
        keys.add("key1");
        keys.add("key2");
        // First lock succeeds, second fails
        when(guard.tryToLock("key1", 1, TimeUnit.SECONDS)).thenReturn(true);
        when(guard.tryToLock("key2", 1, TimeUnit.SECONDS)).thenReturn(false);

        // Act & Assert
        assertThrows(LockAlreadyTakenException.class,
                () -> ConflictsGuardUtils.runWithLocks(guard, keys, 1, TimeUnit.SECONDS, () -> {}));

        // Verify key1 was unlocked even though key2 failed
        verify(guard).unlock("key1");
        verify(guard, never()).unlock("key2");
    }

    @Test
    void runWithLocks_shouldReleaseAllLocks_whenActionThrowsException() throws InterruptedException {
        // Arrange
        @SuppressWarnings("unchecked")
        ConflictsGuard<String> guard = mock(ConflictsGuard.class);
        when(guard.tryToLock(any(), anyLong(), any())).thenReturn(true);
        RuntimeException actionException = new RuntimeException("Action failed");

        // Act & Assert
        Set<String> keys = Set.of("key1", "key2");
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> ConflictsGuardUtils.runWithLocks(guard, keys, 1, TimeUnit.SECONDS,
                        () -> { throw actionException; }));

        assertEquals(actionException, thrown);
        verify(guard, times(2)).unlock(any());
    }

    @Test
    void runWithLocks_shouldContinueUnlocking_whenOneUnlockFails() throws InterruptedException {
        // Arrange
        @SuppressWarnings("unchecked")
        ConflictsGuard<String> guard = mock(ConflictsGuard.class);
        when(guard.tryToLock(any(), anyLong(), any())).thenReturn(true);
        // Use LinkedHashSet to ensure predictable iteration order for the unlock calls
        Set<String> keys = new LinkedHashSet<>();
        keys.add("key1");
        keys.add("key2");
        keys.add("key3");
        doThrow(new RuntimeException("Unlock failed")).when(guard).unlock("key2");
        AtomicBoolean actionExecuted = new AtomicBoolean(false);

        // Act
        ConflictsGuardUtils.runWithLocks(guard, keys, 1, TimeUnit.SECONDS, () -> actionExecuted.set(true));

        // Assert - all three locks should have unlock attempted
        assertTrue(actionExecuted.get());
        verify(guard).unlock("key1");
        verify(guard).unlock("key2");
        verify(guard).unlock("key3");
    }
}
