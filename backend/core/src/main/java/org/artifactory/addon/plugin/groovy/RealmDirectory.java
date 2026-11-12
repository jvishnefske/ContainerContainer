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

import org.artifactory.api.security.UserGroupService;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.UserInfo;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The user and group store as plugin realms need it: the imperative edge of {@link PluginRealmAuthenticator}.
 * Tests use an in-memory implementation; production uses {@link #over(UserGroupService)}.
 */
public interface RealmDirectory {

    /** @return the user, empty when it does not exist */
    Optional<UserInfo> findUser(String username);

    /** @return the group, empty when it does not exist */
    Optional<GroupInfo> findGroup(String name);

    /**
     * Stores a new user in {@code userRealm} with no password, not admin, with exactly {@code groups}.
     *
     * @return whether the user was created; false when it could not be (for example a concurrent creation)
     */
    boolean createUser(String username, String userRealm, Set<String> groups, Optional<String> email);

    /** Replaces the groups stored on {@code user} with {@code groups}. */
    void replaceGroups(UserInfo user, Set<String> groups);

    /** @return the production directory over Artifactory's user and group service */
    static RealmDirectory over(UserGroupService service) {
        Objects.requireNonNull(service, "service");
        return new RealmDirectory() {
            @Override
            public Optional<UserInfo> findUser(String username) {
                try {
                    return Optional.ofNullable(service.findUser(username, false));
                } catch (UsernameNotFoundException e) {
                    return Optional.empty();
                }
            }

            @Override
            public Optional<GroupInfo> findGroup(String name) {
                return Optional.ofNullable(service.findGroup(name));
            }

            @Override
            public boolean createUser(String username, String userRealm, Set<String> groups, Optional<String> email) {
                UserInfoBuilder builder = new UserInfoBuilder(username)
                        .realm(userRealm)
                        .admin(false)
                        .passwordDisabled(true)
                        .updatableProfile(false)
                        .internalGroups(Set.copyOf(groups));
                email.ifPresent(builder::email);
                return service.createUser(builder.build());
            }

            @Override
            public void replaceGroups(UserInfo user, Set<String> groups) {
                MutableUserInfo copy = InfoFactoryHolder.get().copyUser(user);
                copy.setInternalGroups(Set.copyOf(groups));
                service.updateUser(copy, false);
            }
        };
    }
}
