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

/**
 * Decides whether a caller may run a plugin execution. Pure.
 *
 * <p>Administrators may always run an execution. Anyone else may run it only when it names the caller in
 * {@code users}, names one of the caller's groups in {@code groups}, or lists {@value #ANY_AUTHENTICATED} in
 * {@code users} and the caller is not anonymous (P5). An execution that declares neither is therefore admin-only,
 * and {@code users: ['*']} never opens an execution to anonymous callers.
 */
public final class ExecutionAuthorizer {

    /** The {@code users} entry that admits every authenticated, non-anonymous caller. */
    public static final String ANY_AUTHENTICATED = "*";

    private ExecutionAuthorizer() {
    }

    /** @return whether {@code caller} may run {@code execution} */
    public static boolean isPermitted(PluginExecution execution, Caller caller) {
        if (caller.admin()) {
            return true;
        }
        if (execution.users().contains(caller.username())) {
            return true;
        }
        if (!caller.anonymous() && execution.users().contains(ANY_AUTHENTICATED)) {
            return true;
        }
        for (String group : caller.groups()) {
            if (execution.groups().contains(group)) {
                return true;
            }
        }
        return false;
    }
}
