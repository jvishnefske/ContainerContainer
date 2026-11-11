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

package org.artifactory.storage.db.security.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.security.service.UserLockInMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.artifactory.storage.db.security.service.SecurityCacheUtils.getFromCache;
import static org.artifactory.storage.db.security.service.SecurityCacheUtils.invalidateFromCache;
import static org.artifactory.storage.db.security.service.SecurityCacheUtils.putInCache;

/**
 * @author Noam Shemesh
 */
@Component
public class UserLockInMemoryServiceImpl implements UserLockInMemoryService {
    private static final Logger log = LoggerFactory.getLogger(UserLockInMemoryServiceImpl.class);

    // cache meaning  <username, lock-time>
    private final Cache<String, Long> userAccessUsersCache = CacheBuilder.newBuilder()
            .maximumSize(ConstantValues.maxUsersToTrack.getInt()).
                    expireAfterWrite(24, TimeUnit.HOURS).build();

    private final Cache<String, Long> lockedUsersCache = CacheBuilder.newBuilder()
            .maximumSize(ConstantValues.maxUsersToTrack.getInt()).
                    expireAfterWrite(24, TimeUnit.HOURS).build();

    private final Map<String, AtomicInteger> incorrectLoginAttemptsCache = new ConcurrentHashMap<>();

    private final AccessService accessService;

    @Autowired
    public UserLockInMemoryServiceImpl(AccessService accessService) {
        this.accessService = accessService;
    }

    private long loginBaseDelay() {
        return accessService.getUserLockPolicyConfig().getBaseLoginDelayMillis();
    }

    private long maxBlockedDelay() {
        return accessService.getUserLockPolicyConfig().getMaxLoginDelayMillis();
    }

    private int maxIncorrectLoginAttempts() {
        return accessService.getUserLockPolicyConfig().getMaxLoginDelayIncorrectAttempts();
    }

    private long maxFailedAttemptsForCalculation() {
        return (loginBaseDelay() == 0 ? Integer.MAX_VALUE :
                64 - Long.numberOfLeadingZeros(maxBlockedDelay() / loginBaseDelay()) - 1);
        // ceil log_2 of max wait units
    }

    /**
     * Calculates user login delay multiplier,
     * the value (security.loginBlockDelay) is
     * taken from system properties file,
     * <p/>
     * delay may not exceed
     *
     * @return user login delay multiplier
     */
    private long getLoginDelay(int numberOffAttempts) {
        int punishedAttempts = numberOffAttempts - maxIncorrectLoginAttempts();
        return calculateBackoff(punishedAttempts);
    }

    private long calculateBackoff(int attempts) {
        return (attempts > maxFailedAttemptsForCalculation()) ?
                maxBlockedDelay() :
                loginBaseDelay() + loginBaseDelay() * (long) Math.pow(attempts, 2.5);
    }

    @Override
    public void updateUserAccess(String loginIdentifier, boolean userLockPolicyEnabled, long accessTimeMillis) {
        loginIdentifier = loginIdentifier.toLowerCase();
        if (shouldUpdateUserAccessCache(loginIdentifier, userLockPolicyEnabled)) {
            putInCache(loginIdentifier, userAccessUsersCache, accessTimeMillis);
        }
    }

    @VisibleForTesting
    boolean shouldUpdateUserAccessCache(String id, boolean userLockPolicyEnabled) {
        return StringUtils.isNotBlank(id) && !userLockPolicyEnabled &&
                !UserInfo.isAnonymous(id) && !isUserLocked(id);
    }

    @Override
    public void lockUser(@Nonnull String loginIdentifier) {
        try {
            loginIdentifier = loginIdentifier.toLowerCase();
            synchronized (lockedUsersCache) {
                registerLockedOutUser(loginIdentifier);
            }
        } catch (Exception e) {
            log.debug("Could not lock user, cause: ", e);
            throw new StorageException("Could not lock user, reason: " + e.getMessage());
        }
    }

    @Override
    public boolean isUserLocked(String username) {
        username = username.toLowerCase();
        if (shouldCacheLockedUsers()) {
            return getFromCache(username, lockedUsersCache) != null;
        }

        return false;
    }

    @Override
    public long getNextLoginTime(String loginIdentifier) {
        loginIdentifier = loginIdentifier.toLowerCase();
        Long lastAccessTime = getFromCache(loginIdentifier, userAccessUsersCache);
        if (lastAccessTime != null) {
            return getNextLoginTime(
                    getIncorrectLoginAttempts(loginIdentifier), lastAccessTime, loginIdentifier
            );
        }
        return -1;
    }

    @Override
    public long getNextLoginTime(int incorrectLoginAttempts, long lastAccessTimeMillis, String loginIdentifier) {
        loginIdentifier = loginIdentifier.toLowerCase();
        if (incorrectLoginAttempts >= maxIncorrectLoginAttempts()) {
            long delay = getLoginDelay(incorrectLoginAttempts);
            log.debug("Delayed user: username '{}' login by {} milliseconds due to {} incorrect login attempts",
                    loginIdentifier,
                    delay, incorrectLoginAttempts);
            if (delay != 0) {
                return lastAccessTimeMillis + (Math.min(delay, maxBlockedDelay()));
            }
        }
        return -1;
    }

    @Override
    public void unlockUser(@Nonnull String username) {
        try {
            username = username.toLowerCase();
            synchronized (lockedUsersCache) {
                unregisterLockedOutUser(username);
            }
        } catch (Exception e) {
            log.debug("Could not unlock user: '{}', cause: {}", username, e);
            throw new StorageException("Could not unlock user: '" + username + "', reason: " + e.getMessage());

        }
    }

    @Override
    public void unlockAllUsers() {
        lockedUsersCache.invalidateAll();
    }

    //TODO: [by YS] consider moving this to Access client

    /**
     * Registers locked out user in cache
     */
    private void registerLockedOutUser(String username) {
        username = username.toLowerCase();
        if (shouldCacheLockedUsers()) {
            putInCache(username, lockedUsersCache, System.currentTimeMillis());
        }
    }

    /**
     * @return whether locked out users should be cached
     */
    private boolean shouldCacheLockedUsers() {
        return ConstantValues.useFrontCacheForBlockedUsers.getBoolean();
    }

    /**
     * Unregisters locked out user/s from cache
     *
     * @param user a user name to unlock or all users via ALL_USERS
     *             {@see UserGroupServiceImpl.ALL_USERS}
     */
    private void unregisterLockedOutUser(String user) {
        user = user.toLowerCase();
        if (shouldCacheLockedUsers()) {
            invalidateFromCache(user, lockedUsersCache);
        }
    }

    @Override
    public int getIncorrectLoginAttempts(@Nonnull String loginIdentifier) {
        loginIdentifier = loginIdentifier.toLowerCase();
        AtomicInteger incorrectLoginAttempts = incorrectLoginAttemptsCache.get(loginIdentifier.toLowerCase());
        return incorrectLoginAttempts != null ? incorrectLoginAttempts.get() : 0;
    }

    @Override
    public void registerIncorrectLoginAttempt(@Nonnull String loginIdentifier) {
        String lowerCaseId = loginIdentifier.toLowerCase();
        AtomicInteger atomicInteger = incorrectLoginAttemptsCache.putIfAbsent(lowerCaseId, new AtomicInteger(0));
        if (atomicInteger == null) {
            atomicInteger = new AtomicInteger(0);
            incorrectLoginAttemptsCache.put(lowerCaseId, atomicInteger);
        }
        int incorrectLoginAttempt = atomicInteger.incrementAndGet();
        log.debug("Increased IncorrectLoginAttempts for user '{}' to {}", loginIdentifier, incorrectLoginAttempt);
    }

    @Override
    public void resetIncorrectLoginAttempts(@Nonnull String loginIdentifier) {
        loginIdentifier = loginIdentifier.toLowerCase();
        if (incorrectLoginAttemptsCache.containsKey(loginIdentifier)) {
            incorrectLoginAttemptsCache.remove(loginIdentifier);
            log.debug("Reset IncorrectLoginAttempts for user '{}'", loginIdentifier);
        }
    }
}
