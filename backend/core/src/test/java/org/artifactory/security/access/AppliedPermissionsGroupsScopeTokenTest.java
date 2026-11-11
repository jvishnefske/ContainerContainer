package org.artifactory.security.access;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Set;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;

/**
 * @author Uriah Levy
 */
@Test
public class AppliedPermissionsGroupsScopeTokenTest {
    @Test(dataProvider = "provideValidScopeTokenAndGroupNames")
    public void testParse(String scopeToken, Set<String> groupNames) {
        assertEquals(AppliedPermissionsScopeToken.parse(scopeToken).getGroupNames(), groupNames);
    }

    @Test(dataProvider = "provideValidScopeTokenAndGroupNames")
    public void testGetScopeToken(String scopeToken, Set<String> groupNames) {
        assertEquals(AppliedPermissionsScopeToken.parse(scopeToken).getScopeToken(), scopeToken);
        assertEquals(new AppliedPermissionsScopeToken(groupNames).getScopeToken(), scopeToken);
        assertEquals(new AppliedPermissionsScopeToken(groupNames).toString(), scopeToken);
    }

    @Test(dataProvider = "provideScopeTokens")
    public void testAccepts(String scopeToken, boolean expected) {
        assertEquals(AppliedPermissionsScopeToken.accepts(scopeToken), expected);
    }

    @Test(dataProvider = "provideIdentityTokens")
    public void testIdentityTokenRegex(String scopeToken, boolean expected) {
        assertEquals(AppliedPermissionsScopeToken.SCOPE_APPLIED_PERMISSIONS_USER_PATTERN.matcher(scopeToken).matches(),
                expected);
    }

    @Test(dataProvider = "provideIdentityTokens")
    public void testIsIdentityToken(String scope, boolean expected) {
        assertEquals(AppliedPermissionsScopeToken.isIdentityToken(ImmutableList.of(scope)), expected);
    }

    @DataProvider
    public static Object[][] provideValidScopeTokenAndGroupNames() {
        return new Object[][] {
                {"applied-permissions/groups:g1", Sets.newLinkedHashSet(asList("g1"))},
                {"applied-permissions/groups:g1,g2", Sets.newLinkedHashSet(asList("g1", "g2"))},
                {"applied-permissions/groups:g1,g2,g3", Sets.newLinkedHashSet(asList("g1", "g2", "g3"))},
                {"applied-permissions/groups:readers", Sets.newLinkedHashSet(asList("readers"))},
                {"applied-permissions/groups:readers,deployers", Sets.newLinkedHashSet(asList("readers", "deployers"))},
                {"applied-permissions/groups:*", Sets.newLinkedHashSet(asList("*"))}
        };
    }

    @DataProvider
    public static Object[][] provideScopeTokens() {
        return new Object[][] {
                {"applied-permissions/groups:g1", true},
                {"applied-permissions/groups:g1,g2", true},
                {"applied-permissions/groups:g1,g2,g3", true},
                {"applied-permissions/groups:readers", true},
                {"applied-permissions/groups:readers,deployers", true},
                {"applied-permissions/groups:*", true},
                {"applied-permissions/groups:g1,g2\\", false},
                {"applied-permissions/groups:g1,\\g2", false},
                {"applied-permissions/groups:g1\\,g2", false},
                {"applied-permissions/groups:\\g1,g2", false},
                {"applied-permissions/groups:\"g1,g2 with space\"", true},
                {"applied-permissions/groups:\"g1 with space,g2", true},
                {"applied-permissions/groups :g1,g2", false},
                {"applied-permission/groups:g1,g2", false},
                {"\\applied-permissions/groups:g1,g2", false},
                {"foo:g1", false},
                {"", false},
                {null, false},
        };
    }

    @DataProvider
    public static Object[][] provideIdentityTokens() {
        return new Object[][] {
                {"applied-permissions/user", true},
                {"123applied-permissions/user", false},
                {"applied-permissions/groups:g1,g2,g3", false},
                {"applied-permissions/ user123", false},
                {"applied-permissions:user", false},
                {"", false},
        };
    }

}