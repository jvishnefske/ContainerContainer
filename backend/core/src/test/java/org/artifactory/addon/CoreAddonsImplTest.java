package org.artifactory.addon;

import com.jfrog.ph.acl.model.curation.CurationPackageForRequest;
import com.jfrog.ph.acl.model.curation.CurationRequest;
import com.jfrog.ph.acl.model.curation.CurationVersionForRequest;
import lombok.SneakyThrows;
import org.artifactory.addon.curation.CurationCachedPackageStatus;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.common.repo.PackageType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.util.UnsupportedByLicenseException;
import org.jfrog.worker.proto.execution.Action;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.artifactory.addon.curation.SecurityDecision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Tomer Slutsky
 */
class CoreAddonsImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void getFullSyncRepos() {
        HaCommonAddon commonAddon = new CoreAddonsImpl();
        Set<String> fullSyncingRepos = commonAddon.getFullSyncingRepos(CoreAddonsImpl.class);
        assertEquals(0, fullSyncingRepos.size());
    }

    @Test
    @SneakyThrows
    void testCoreAddonsImplCuration() {
        CoreAddonsImpl coreAddons = new CoreAddonsImpl();
        assertThrows(UnsupportedByLicenseException.class, () -> coreAddons.toggleCuration(null));
        assertFalse(coreAddons.isUserAllowed(null));
        assertFalse(coreAddons.isCurationEntitled());
        assertFalse(coreAddons.isCurationEnabled());
        assertTrue(coreAddons.isCurationConfigValid(null));
        assertEquals(List.of(), coreAddons.getSupportedPackageTypes());
        assertEquals(List.of(), coreAddons.getSupportedRepositories());
        assertFalse(coreAddons.getCurationStatus().status());
        CurationCachedPackageStatus context = coreAddons.getPackageStatus(null, null, null, null, false);
        assertEquals(SecurityDecision.CURATION_NOT_ENABLED, context.status().securityDecision());
        assertThrows(UnsupportedByLicenseException.class, coreAddons::invalidatePackageStatusCache);
        coreAddons.sendCachedPackageEvent(null, null);
        assertEquals(List.of(), coreAddons.getCurationPackageTypes());
        assertEquals(SecurityDecision.CURATION_NOT_ENABLED,
                coreAddons.getCurationPackageStatus(null, false, false).securityDecision());
        coreAddons.invalidateCurationPackageStatusCache();
        coreAddons.sendCuratedRepoEvent(null);
        coreAddons.sendCachedPackageEvent(null);
        assertThrows(UnsupportedByLicenseException.class, () -> coreAddons.audit(null, null, null));
        assertFalse(coreAddons.shouldPerformPassThrough(null, null));
        assertFalse(coreAddons.log(null));
        assertThrows(UnsupportedByLicenseException.class, coreAddons::invalidateSettingsCache);
        assertFalse(coreAddons.isBlockDownloadEnabled(null));
        assertEquals(SecurityDecision.CURATION_NOT_ENABLED,
                coreAddons.getCurationCachedPackageStatus(null, false).securityDecision());
        assertEquals(SecurityDecision.CURATION_NOT_ENABLED,
                coreAddons.getPackageStatus(null, null, null, null, false).status().securityDecision());
        assertEquals(SecurityDecision.CURATION_NOT_ENABLED,
                coreAddons.getCachedPackageStatus(null, null, null, null).securityDecision());
        assertThrows(UnsupportedByLicenseException.class, () -> coreAddons.updateInterfaceSettings(null));
        assertFalse(coreAddons.getInterfaceSettings().allowDownloadWhenUnavailable());
        assertFalse(coreAddons.isAllowDownloadWhenUnavailable());
    }

    @Test
    void testRunWorkerAutoInjectedSecurityAndRepoConfig() {
        CoreAddonsImpl coreAddons = new CoreAddonsImpl();
        coreAddons.runWorkerAutoInjectedSecurityAndRepoConfig(Action.ALT_REMOTE_PATH);
        assertTrue(true);
    }

    @Test
    void testRunWorkerAsyncAutoInjectedSecurityAndRepoConfig() {
        CoreAddonsImpl coreAddons = new CoreAddonsImpl();
        coreAddons.runWorkerAsyncAutoInjectedSecurityAndRepoConfig(Action.ALT_REMOTE_PATH);
        assertTrue(true);
    }

    @Test
    void testRunWorkerV2() {
        CoreAddonsImpl coreAddons = new CoreAddonsImpl();
        coreAddons.run(SupportedUnifiedEvents.AFTER_CREATE);
        assertTrue(true);
    }

    @Test
    void testFilterBlockedVersions() {
        CoreAddonsImpl coreAddons = new CoreAddonsImpl();
        CurationRequest request = new CurationRequest(
                "maven-local",
                PackageType.PYPI,
                List.of(new CurationPackageForRequest("package",
                        List.of(new CurationVersionForRequest("1.0.0", "url", Instant.now())))),
                "user"
        );
        assertNull(coreAddons.filterBlockedVersions(request));
    }
}
