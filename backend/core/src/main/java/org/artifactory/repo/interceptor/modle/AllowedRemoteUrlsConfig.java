package org.artifactory.repo.interceptor.modle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.repo.RepoType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Model for the allowed remote URLs configuration JSON.
 * <p>
 * JSON structure:
 * <pre>
 * {
 *   "globallyAllowedUrls": {
 *     "https://registry.npmjs.org": ["Npm"],
 *     "https://repo1.maven.org": ["Gradle", "Ivy", "Maven", "P2", "Sbt"]
 *   },
 *   "explicitlyDeniedUrlRoute": {
 *     "https://nodejs.org/dist": ["Npm"]
 *   }
 * }
 * </pre>
 * {@code globallyAllowedUrls} uses prefix matching: if a URL starts with
 * a known prefix, the package type must be in the allowed set.
 * {@code explicitlyDeniedUrlRoute} uses exact matching: if a URL equals
 * a denied route and the package type is listed, the action is blocked.
 * Denied routes are evaluated before the prefix-based allowlist.
 * URLs that match neither are always allowed (custom/private registries).
 *
 * @author Dor Shriki
 */
@Data
@Slf4j
public class AllowedRemoteUrlsConfig {

    private Map<String, Set<String>> globallyAllowedUrls = new HashMap<>();
    private Map<String, Set<String>> explicitlyDeniedUrlRoute = new HashMap<>();

    @JsonIgnore
    private transient Map<String, Set<RepoType>> resolvedMappings;
    @JsonIgnore
    private transient Map<String, Set<RepoType>> resolvedDeniedRoutes;

    @JsonIgnore
    public Map<String, Set<RepoType>> getResolvedMappings() {
        if (resolvedMappings == null) {
            resolvedMappings = resolveTypeMap(globallyAllowedUrls);
        }
        return resolvedMappings;
    }

    @JsonIgnore
    public Map<String, Set<RepoType>> getResolvedDeniedRoutes() {
        if (resolvedDeniedRoutes == null) {
            resolvedDeniedRoutes = resolveTypeMap(explicitlyDeniedUrlRoute);
        }
        return resolvedDeniedRoutes;
    }

    private Map<String, Set<RepoType>> resolveTypeMap(Map<String, Set<String>> raw) {
        Map<String, Set<RepoType>> result = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : raw.entrySet()) {
            String url = entry.getKey().toLowerCase();
            Set<RepoType> types = new HashSet<>();
            for (String typeName : entry.getValue()) {
                RepoType repoType = RepoType.fromTypeWithNullable(typeName);
                if (repoType == null) {
                    log.warn("Unknown repository type '{}' for URL '{}'", typeName, entry.getKey());
                    continue;
                }
                types.add(repoType);
            }
            result.put(url, types);
        }
        return result;
    }

    /**
     * Checks whether the given URL and package type combination is allowed.
     * Evaluation order:
     * 1. Explicitly denied routes (exact match) — blocked if type is in the denied set.
     * 2. Globally allowed URLs (prefix match) — allowed only if type is in the allowed set.
     * 3. No match — allowed (custom/private registry).
     */
    public boolean isUrlAllowed(String url, RepoType packageType) {
        if (url == null || packageType == null || packageType == RepoType.Generic) {
            return true;
        }
        String lowerUrl = url.toLowerCase();
        for (Map.Entry<String, Set<RepoType>> entry : getResolvedDeniedRoutes().entrySet()) {
            if (lowerUrl.equals(entry.getKey())) {
                return !entry.getValue().contains(packageType);
            }
        }
        for (Map.Entry<String, Set<RepoType>> entry : getResolvedMappings().entrySet()) {
            if (lowerUrl.startsWith(entry.getKey())) {
                return entry.getValue().contains(packageType);
            }
        }
        return true;
    }
}
