package org.artifactory.api.module;

import org.artifactory.descriptor.repo.RepoLayout;

/**
 * Cache key for {@link ModuleInfoUtils#LAYOUT_PATTERN_CACHE}. Snapshots all {@link RepoLayout}
 * fields so any layout change yields a new key, avoiding stale compiled patterns.
 * {@link RepoLayout} itself is not used as the key because its {@code hashCode()} is name-only.
 */
record PatternCacheKey(
        String patternToUse,
        boolean supportVersionsTokens,
        String name,
        String artifactPathPattern,
        boolean distinctiveDescriptorPathPattern,
        String descriptorPathPattern,
        String folderIntegrationRevisionRegExp,
        String fileIntegrationRevisionRegExp) {

    static PatternCacheKey of(RepoLayout layout, String patternToUse, boolean supportVersionsTokens) {
        return new PatternCacheKey(patternToUse, supportVersionsTokens,
                layout.getName(),
                layout.getArtifactPathPattern(),
                layout.isDistinctiveDescriptorPathPattern(),
                layout.getDescriptorPathPattern(),
                layout.getFolderIntegrationRevisionRegExp(),
                layout.getFileIntegrationRevisionRegExp());
    }
}
