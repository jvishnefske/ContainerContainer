package org.artifactory.security.access;

import com.google.common.collect.Sets;
import org.jfrog.access.util.TokenScopeUtils;

import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;
import static org.artifactory.security.access.AccessServiceConstants.APPLIED_PERMISSIONS;
import static org.jfrog.access.token.JwtAccessToken.SCOPE_TOKEN_PATTERN;
import static org.jfrog.access.token.JwtAccessToken.SCOPE_TOKEN_REGEX;

/**
 * @author Uriah Levy
 */
public class AppliedPermissionsScopeToken {
    static final String APPLIED_PERMISSIONS_GROUPS_PREFIX = APPLIED_PERMISSIONS + "/groups:";
    private static final String GROUP_DELIMITER = ",";
    static final Pattern SCOPE_APPLIED_PERMISSIONS_GROUPS_PATTERN = Pattern.compile(
            //applied-permissions/groups:g1,g2,g3  (at least one group)
            APPLIED_PERMISSIONS_GROUPS_PREFIX + SCOPE_TOKEN_REGEX + "(" + GROUP_DELIMITER + SCOPE_TOKEN_REGEX + ")*");
    private static final String USER_SUFFIX = "/user";
    static final Pattern SCOPE_APPLIED_PERMISSIONS_USER_PATTERN = Pattern.compile(
            //applied-permissions/user
            "^" + APPLIED_PERMISSIONS + USER_SUFFIX);
    public static final String APPLIED_PERMISSIONS_USER = "applied-permissions/user";
    private final Set<String> groupNames = Sets.newLinkedHashSet(); //predictable order

    AppliedPermissionsScopeToken(@Nonnull Set<String> groupNames) {
        assertValidGroupNames(groupNames);
        this.groupNames.addAll(groupNames);
    }

    /**
     * Check whether a scope token is a valid applied-permissions/groups scope token
     *
     * @param scopeToken the scope token to parse
     */
    public static boolean accepts(String scopeToken) {
        return scopeToken != null && SCOPE_APPLIED_PERMISSIONS_GROUPS_PATTERN.matcher(scopeToken).matches();
    }

    /**
     * Parse a scope token as applied-permissions/groups
     *
     * @param scopeToken the scope token to parse
     * @return the parsed instance
     */
    @Nonnull
    public static AppliedPermissionsScopeToken parse(String scopeToken) {
        if (!accepts(scopeToken)) {
            throw new IllegalArgumentException("Not a valid applied-permissions/groups scope token:" + scopeToken);
        }
        Set<String> groupNameSet = TokenScopeUtils.scopeToGroups(scopeToken);
        return new AppliedPermissionsScopeToken(groupNameSet);
    }

    private void assertValidGroupNames(@Nonnull Set<String> groupNames) {
        if (requireNonNull(groupNames, "group names are required").isEmpty()) {
            throw new IllegalArgumentException("at least one group name is required");
        }
        groupNames.forEach(groupName -> {
            if (!SCOPE_TOKEN_PATTERN.matcher(groupName).matches()) {
                throw new IllegalArgumentException("Group name is not a valid scope token: " + groupName);
            }
        });
    }

    /**
     * Get the group names in this scope token
     */
    @Nonnull
    public Set<String> getGroupNames() {
        return Collections.unmodifiableSet(groupNames);
    }

    /**
     * Get the formatted scope token
     */
    @Nonnull
    public String getScopeToken() {
        return APPLIED_PERMISSIONS_GROUPS_PREFIX + String.join(GROUP_DELIMITER, groupNames);
    }

    @Override
    public String toString() {
        return getScopeToken();
    }

    public static boolean isIdentityToken(List<String> scope) {
        return scope.stream().anyMatch(AppliedPermissionsScopeToken.APPLIED_PERMISSIONS_USER::equals);
    }
}
