package org.artifactory.repo.system;

import org.testng.annotations.Test;

import static org.artifactory.repo.system.SystemRepos.LOG_SHIPPING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.isDeleteOnlyBySuperUser;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.isImportAllowed;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class SystemReposImportAllowedTest {

    @Test
    public void testImportAllowed() {
        assertTrue(isImportAllowed(SUPPORT_BUNDLE_REPO_KEY), "allowed by default");
        assertTrue(isImportAllowed("not-system-repo"), "allowed for not system repos");
        assertFalse(isImportAllowed(USAGE_REPO_KEY), "not allowed for USAGE_REPO");
        assertFalse(isImportAllowed(LOG_SHIPPING_REPO_KEY), "not allowed for LOG_SHIPPING_REPO");
    }

    @Test
    public void testDeleteOnlyBySuperUser() {
        assertFalse(isDeleteOnlyBySuperUser(SUPPORT_BUNDLE_REPO_KEY),
                "any user can delete system repo content by default");
        assertFalse(isDeleteOnlyBySuperUser("not-system-repo"),
                "any user can delete non system repo content");
        assertTrue(isDeleteOnlyBySuperUser(USAGE_REPO_KEY), "only super user can delete USAGE_REPO content");
        assertFalse(isDeleteOnlyBySuperUser(LOG_SHIPPING_REPO_KEY), "any user can delete LOG_SHIPPING_REPO content");
    }

}