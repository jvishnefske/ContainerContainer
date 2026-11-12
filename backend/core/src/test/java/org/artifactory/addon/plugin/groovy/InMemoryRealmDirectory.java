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

import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.MutableGroupInfo;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.UserInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Test double of the user and group store; records every write. */
final class InMemoryRealmDirectory implements RealmDirectory {

    final Map<String, UserInfo> users = Collections.synchronizedMap(new LinkedHashMap<>());
    final Map<String, GroupInfo> groups = Collections.synchronizedMap(new LinkedHashMap<>());
    final List<String> writes = Collections.synchronizedList(new ArrayList<>());
    int lookups;

    InMemoryRealmDirectory group(String name, boolean admin) {
        MutableGroupInfo group = InfoFactoryHolder.get().createGroup(name);
        group.setAdminPrivileges(admin);
        groups.put(name, group);
        return this;
    }

    InMemoryRealmDirectory user(String name, String realm, boolean admin, String... groupNames) {
        MutableUserInfo user = new UserInfoBuilder(name).realm(realm).admin(admin)
                .internalGroups(Set.of(groupNames)).build();
        users.put(name, user);
        return this;
    }

    Set<String> groupsOf(String username) {
        return RealmGrantPolicy.ExistingUser.of(users.get(username)).storedGroups();
    }

    @Override
    public Optional<UserInfo> findUser(String username) {
        lookups++;
        return Optional.ofNullable(users.get(username));
    }

    @Override
    public Optional<GroupInfo> findGroup(String name) {
        lookups++;
        return Optional.ofNullable(groups.get(name));
    }

    @Override
    public boolean createUser(String username, String userRealm, Set<String> groupNames, Optional<String> email) {
        writes.add("create " + username + " " + userRealm + " " + groupNames);
        if (users.containsKey(username)) {
            return false;
        }
        UserInfoBuilder builder = new UserInfoBuilder(username).realm(userRealm).admin(false)
                .passwordDisabled(true).updatableProfile(false).internalGroups(Set.copyOf(groupNames));
        email.ifPresent(builder::email);
        users.put(username, builder.build());
        return true;
    }

    @Override
    public void replaceGroups(UserInfo user, Set<String> groupNames) {
        writes.add("groups " + user.getUsername() + " " + groupNames);
        MutableUserInfo copy = InfoFactoryHolder.get().copyUser(user);
        copy.setInternalGroups(Set.copyOf(groupNames));
        users.put(user.getUsername(), copy);
    }
}
