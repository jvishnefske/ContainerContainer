/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 * Modifications copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import org.springframework.security.authentication.AccountStatusException;

/**
 * A plugin realm claimed the credentials and refused them, or Java refused what the realm granted.
 *
 * <p>It extends {@link AccountStatusException} because Spring's {@code ProviderManager} stops at that type instead
 * of asking the next provider, so a rejection is final. The message is generic; the reason is only logged, at
 * debug level, so neither credentials nor realm internals reach the client.
 */
public class PluginRealmRejectedException extends AccountStatusException {

    /** Message every rejection carries. */
    public static final String MESSAGE = "The credentials were refused by a plugin realm";

    public PluginRealmRejectedException() {
        super(MESSAGE);
    }
}
