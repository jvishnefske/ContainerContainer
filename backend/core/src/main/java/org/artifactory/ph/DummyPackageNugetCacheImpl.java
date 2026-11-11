package org.artifactory.ph;

import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.service.PackageNugetCache;

import java.util.Collections;
import java.util.List;

public class DummyPackageNugetCacheImpl implements PackageNugetCache {

    @Override
    public void removeNuPkgFromCache(PackageArtifact affectedItem) {
        // noop
    }

    @Override
    public void addNuPkgToRepoCache(PackageArtifact artifact, boolean async) {
        // noop
    }

    @Override
    public List<PackageArtifact> searchNuPkg(String repoKey, String query, boolean isSemVer2Endpoint) {
        return Collections.emptyList();
    }

    @Override
    public List<PackageArtifact> searchV2NuPkg(String repoKey, String searchTerm, boolean includePreRelease,
            boolean shouldRemoveSemVer2Results) {
        return Collections.emptyList();
    }

    @Override
    public void recalculateCache(String repoKey, boolean force) {
        // noop
    }
}
