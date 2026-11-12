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

package org.artifactory.addon.plugin.wasm;

/** The hooks of ABI v1, with the spelling of the envelope's {@code hook} field. */
public enum Hook {
    /** A realm is offered a user name and password. */
    REALM_AUTHENTICATE("realm.authenticate"),
    /** A realm is offered a bearer token. */
    REALM_BEARER("realm.bearer"),
    /** A named execution runs. */
    EXECUTION("execution");

    private final String wireName;

    Hook(String wireName) {
        this.wireName = wireName;
    }

    /** @return the envelope spelling */
    public String wireName() {
        return wireName;
    }

    /** @return whether this is one of the realm hooks */
    public boolean isRealm() {
        return this != EXECUTION;
    }
}
