/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.artifactory.security.access.embedded.core.EaGroup;
import org.artifactory.security.access.embedded.core.EaUser;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.client.auth.LoginResult;
import org.jfrog.access.client.auth.ServicePrincipal;
import org.jfrog.access.client.auth.UserPrincipal;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.common.SubjectFQN;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.model.MessageModelCode;
import org.jfrog.access.model.Realm;
import org.jfrog.access.model.UserStatus;
import org.jfrog.access.rest.user.UserWithGroupsResponse;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.user.UserMFAStatus;
import org.jfrog.access.user.UsersUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Password and token authentication shared by the {@code auth()} and {@code grpcAuth()} surfaces.
 * <p>
 * Password rules (IA1, IA5): the presented password is always compared, against a dummy when the user is unknown
 * or has no password, so timing does not reveal accounts. A user with no password hash, a disabled user and a
 * locked user never authenticate by password. Status is reported only after the password matched, so it is not
 * disclosed to someone without the password. Failures are {@link AccessClientHttpException}s with the status and
 * error code Artifactory's authentication providers map to Spring Security exceptions: 401 {@code UNAUTHORIZED}
 * (bad credentials), 401 {@code DISABLED}, 429 {@code TOO_MANY_REQUESTS} (locked) and 401 {@code PASSWORD_EXPIRED}.
 * <p>
 * Token rules: a JWT presented as a password is verified by {@link EaTokenClient#verify}; a user subject becomes a
 * {@link UserPrincipal} (a transient one if the user does not exist, refused if the user is disabled or locked)
 * and a service subject a {@link ServicePrincipal}.
 */
final class EaAuthenticator {

    private final EaRuntime rt;
    private final EaTokenClient tokens;

    EaAuthenticator(EaRuntime rt, EaTokenClient tokens) {
        this.rt = rt;
        this.tokens = tokens;
    }

    /**
     * Authenticates a username and password.
     *
     * @param username the username
     * @param password the password
     * @return the user
     * @throws AccessClientHttpException when authentication fails
     */
    EaUser byPassword(String username, String password) {
        Optional<EaUser> found = username == null || username.isBlank() ? Optional.empty()
                : EaErrors.orThrow(rt.store().getUser(username)).or(
                        () -> EaErrors.orThrow(rt.store().findUserByUsernameIgnoreCase(username)));
        String presented = password == null ? "" : password;
        boolean matches = rt.passwords().matches(presented, found.map(EaUser::passwordHash).orElse(null));
        if (found.isEmpty() || !matches || found.get().passwordHash() == null) {
            throw badCredentials();
        }
        EaUser user = found.get();
        assertCanLogIn(user);
        if (Boolean.parseBoolean(user.customData().get(EaModels.PASSWORD_EXPIRED))) {
            throw EaErrors.http(EaErrors.UNAUTHORIZED, MessageModelCode.CODE_PASSWORD_EXPIRED, "Password has expired");
        }
        return user;
    }

    /**
     * Authenticates a presented credential that may be a token: a JWT goes to {@link #byToken}, anything else to
     * {@link #byPassword}.
     *
     * @param username the username, possibly empty for a token
     * @param secret   the password or token
     * @return the login result, always successful
     * @throws AccessClientHttpException when authentication fails
     */
    LoginResult login(String username, String secret) {
        if (secret != null && tokens.getAccessTokenType(secret)
                == org.jfrog.access.token.AccessTokenType.JWT_ACCESS_TOKEN) {
            return byToken(secret);
        }
        EaUser user = byPassword(username, secret);
        return new Result(principal(user, Set.of()));
    }

    /**
     * Authenticates a token.
     *
     * @param token the token
     * @return the login result, always successful
     * @throws AccessClientHttpException when the token does not verify or its user cannot log in
     */
    LoginResult byToken(String token) {
        TokenVerifyResult result = tokens.verify(token);
        if (!result.isSuccessful() || result.getAccessToken() == null) {
            throw EaErrors.http(EaErrors.UNAUTHORIZED, MessageModelCode.CODE_TOKEN_VERIFICATION_FAILED,
                    result.getReason() == null ? "invalid" : result.getReason());
        }
        JwtAccessToken jwt = result.getAccessToken();
        Set<String> scopes = new LinkedHashSet<>(jwt.getScope());
        String subject = jwt.getSubject();
        if (SubjectFQN.isUserTokenSubject(subject)) {
            String username = SubjectFQN.extractUsername(subject);
            Optional<EaUser> user = EaErrors.orThrow(rt.store().getUser(username));
            if (user.isPresent()) {
                assertCanLogIn(user.get());
                return new Result(principal(user.get(), scopes));
            }
            UserWithGroupsResponse transientUser = new UserWithGroupsResponse(username).realm(Realm.INTERNAL)
                    .status(UserStatus.ENABLED).mfaStatus(UserMFAStatus.NONE).transientUser(true)
                    .customData(Map.of()).groups(List.of());
            return new Result(new UserPrincipal(transientUser, scopes, Set.of(), Set.of()));
        }
        try {
            return new Result(new ServicePrincipal(ServiceId.fromFormattedName(subject), List.copyOf(scopes)));
        } catch (RuntimeException e) {
            throw EaErrors.http(EaErrors.UNAUTHORIZED, MessageModelCode.CODE_TOKEN_VERIFICATION_FAILED,
                    "unknown token subject");
        }
    }

    /**
     * Builds the principal Artifactory's providers expect for a user.
     *
     * @param user   the user
     * @param scopes the token scopes, empty for a password login
     * @return the principal
     */
    UserPrincipal principal(EaUser user, Set<String> scopes) {
        List<EaGroup> groups = groupsOf(user);
        Set<UsersUtil.BuiltInUserProperty> userProps = builtIns(EaModels.visibleCustomData(user));
        Set<UsersUtil.BuiltInUserProperty> groupProps = EnumSet.noneOf(UsersUtil.BuiltInUserProperty.class);
        for (EaGroup g : groups) {
            groupProps.addAll(builtIns(EaModels.toGroup(g).getCustomData()));
        }
        return new UserPrincipal(EaModels.toUserWithGroups(user, groups), scopes, userProps, groupProps);
    }

    /**
     * Reads the groups of a user, skipping any that vanished concurrently.
     *
     * @param user the user
     * @return the groups
     */
    List<EaGroup> groupsOf(EaUser user) {
        List<EaGroup> out = new ArrayList<>();
        for (String name : user.groups()) {
            EaErrors.orThrow(rt.store().getGroup(name)).ifPresent(out::add);
        }
        return out;
    }

    private static Set<UsersUtil.BuiltInUserProperty> builtIns(Map<String, String> customData) {
        Set<UsersUtil.BuiltInUserProperty> out = EnumSet.noneOf(UsersUtil.BuiltInUserProperty.class);
        customData.forEach((k, v) -> {
            if (UsersUtil.BuiltInUserProperty.contains(k) && Boolean.parseBoolean(v)) {
                out.add(UsersUtil.BuiltInUserProperty.fromName(k));
            }
        });
        return out;
    }

    /**
     * Refuses a user who may not log in: disabled answers 401, locked answers 429.
     *
     * @param user the user
     * @throws AccessClientHttpException when the user is disabled or locked
     */
    static void assertCanLogIn(EaUser user) {
        switch (user.status()) {
            case ENABLED -> {
            }
            case DISABLED -> throw EaErrors.http(EaErrors.UNAUTHORIZED, MessageModelCode.CODE_DISABLED,
                    "User is disabled");
            case LOCKED -> throw EaErrors.http(429, MessageModelCode.CODE_TOO_MANY_REQUESTS, "User is locked");
        }
    }

    private static AccessClientHttpException badCredentials() {
        return EaErrors.http(EaErrors.UNAUTHORIZED, MessageModelCode.CODE_UNAUTHORIZED, "Bad credentials");
    }

    /** A successful login. */
    private record Result(AccessPrincipal getPrincipal) implements LoginResult {
        @Override
        public boolean isSuccessful() {
            return true;
        }

        @Override
        public String getReason() {
            return null;
        }

        @Override
        public String getCookie() {
            return null;
        }
    }
}
