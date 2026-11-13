/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.storage.db.security.service.access;

/**
 * Expires or revalidates the passwords of every user directly, without the raw Access REST call.
 * <p>
 * Stock Artifactory reaches these operations through {@code AccessClient.restCall} on
 * {@code /api/v2/users/(un)expire_password_for_all_users}. An Access client that also implements this interface
 * (the embedded Access client) is called through it instead, see
 * {@link AccessUserGroupStoreService#expirePasswordForAllUsers()}.
 */
public interface BulkPasswordExpiry {

    /**
     * Sets or clears the password-expired flag of every user, in one transaction.
     * <p>
     * Expiring touches only users that have a password; revalidating clears the flag wherever it is set.
     *
     * @param expired {@code true} to expire, {@code false} to revalidate
     * @return the number of users changed
     * @throws RuntimeException if the change could not be stored; nothing is changed then
     */
    int setPasswordExpiredForAllUsers(boolean expired);
}
