package org.artifactory.repo.service;

import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.BasicRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.cache.RepoConfigCache;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VirtualResolveScopeOverlayTest extends ArtifactoryHomeBoundJUnitTest {

    private RepoConfigCache repoConfigCache;
    private Map<String, RepoConfig> repoConfigs;

    @BeforeEach
    void setUp() {
        getBound().setProperty(ConstantValues.virtualResolveConfigThreadLocalCacheEnabled, "true");
        repoConfigs = new HashMap<>();
        repoConfigs.put("local-repo", LocalRepoConfig.create("local-repo", RepoType.Generic));
        repoConfigCache = createRepoConfigCache();
    }

    @AfterEach
    void tearDown() {
        repoConfigCache.destroy();
    }

    @Test
    void getByKey_shouldReturnScopedConfig_whenCalledTwiceWithinVirtualResolve() throws Exception {
        repoConfigCache.runWithVirtualResolveConfigCache("virtual-repo", () -> {
            // R-LOG-11: cache read-through should reuse the request-scoped value.
            assertSame(repoConfigCache.getByKey("local-repo"), repoConfigCache.getByKey("local-repo"));
            return null;
        });
    }

    @Test
    void getRepositoryConfigsByKeys_shouldSeedScopedConfig_whenLoadedInBatch() throws Exception {
        repoConfigCache.runWithVirtualResolveConfigCache("virtual-repo", () -> {
            // R-LOG-11: batch loads should seed the same request-scoped cache as single-key loads.
            RepoConfig batchConfig = repoConfigCache.getRepositoryConfigsByKeys(Set.of("local-repo")).iterator().next();

            assertSame(batchConfig, repoConfigCache.getByKey("local-repo"));
            return null;
        });
    }

    @Test
    void runWithVirtualResolveConfigCache_shouldShareScopedConfigUntilOuterScopeCloses() throws Exception {
        RepoConfig scopedConfig = repoConfigCache.runWithVirtualResolveConfigCache("virtual-repo", () -> {
            // R-IB-1.2: nested and post-close branches need distinct assertions.
            RepoConfig outerConfig = repoConfigCache.getByKey("local-repo");
            repoConfigCache.runWithVirtualResolveConfigCache("virtual-repo", () -> {
                assertSame(outerConfig, repoConfigCache.getByKey("local-repo"));
                return null;
            });
            assertSame(outerConfig, repoConfigCache.getByKey("local-repo"));
            return outerConfig;
        });

        assertNotSame(scopedConfig, repoConfigCache.getByKey("local-repo"));
    }

    @Test
    void runWithVirtualResolveConfigCache_shouldSkipScopedConfig_whenFeatureFlagOff() throws Exception {
        getBound().setProperty(ConstantValues.virtualResolveConfigThreadLocalCacheEnabled, "false");

        repoConfigCache.runWithVirtualResolveConfigCache("virtual-repo", () -> {
            // R-LOG-16: feature flag off preserves normal per-call cache-copy behavior.
            assertNotSame(repoConfigCache.getByKey("local-repo"), repoConfigCache.getByKey("local-repo"));
            return null;
        });
    }

    @Test
    void getByKey_shouldReloadConfig_whenScopedEntryIsInvalidatedByUpdate() throws Exception {
        LocalRepoConfig updatedConfig = LocalRepoConfig.create("local-repo", RepoType.Maven);

        repoConfigCache.runWithVirtualResolveConfigCache("virtual-repo", () -> {
            // R-LOG-11: same-thread repo config updates must evict stale scoped entries.
            RepoConfig beforeUpdate = repoConfigCache.getByKey("local-repo");
            repoConfigs.put("local-repo", updatedConfig);
            repoConfigCache.updateRepoConfigKeys(Map.of("local-repo", new BasicRepoConfig(updatedConfig)));
            RepoConfig afterUpdate = repoConfigCache.getByKey("local-repo");

            assertNotSame(beforeUpdate, afterUpdate);
            assertEquals(RepoType.Maven, afterUpdate.getPackageType());
            return null;
        });
    }

    @Test
    void runWithVirtualResolveConfigCache_shouldClearScopedConfig_whenSupplierThrows() {
        AtomicReference<RepoConfig> scopedConfig = new AtomicReference<>();

        assertThrows(IllegalStateException.class, () -> repoConfigCache.runWithVirtualResolveConfigCache("virtual-repo",
                () -> {
                    // R-IB-1.2: error path must clean up the request-scoped cache.
                    scopedConfig.set(repoConfigCache.getByKey("local-repo"));
                    throw new IllegalStateException("boom");
                }));

        assertNotSame(scopedConfig.get(), repoConfigCache.getByKey("local-repo"));
    }

    private RepoConfigCache createRepoConfigCache() {
        Function<Set<String>, Map<String, RepoConfig>> repoConfigsByKeys = keys -> {
            Map<String, RepoConfig> configsByKey = new HashMap<>();
            keys.forEach(key -> {
                if (repoConfigs.containsKey(key)) {
                    configsByKey.put(key, repoConfigs.get(key));
                }
            });
            return configsByKey;
        };
        return new RepoConfigCache(60_000, 60_000, 100, 1.0,
                () -> new HashMap<>(repoConfigs),
                repoConfigs::get,
                repoConfigsByKeys,
                ignored -> {
                },
                () -> Map.of("local-repo", new BasicRepoConfig(repoConfigs.get("local-repo"))));
    }
}
