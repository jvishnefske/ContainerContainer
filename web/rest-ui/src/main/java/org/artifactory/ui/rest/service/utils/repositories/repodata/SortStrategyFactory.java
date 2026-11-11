package org.artifactory.ui.rest.service.utils.repositories.repodata;

import lombok.experimental.UtilityClass;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.service.RepoKeyAutoCompleteComparator;

import java.util.Comparator;

@UtilityClass
public class SortStrategyFactory {

    public static final String LOCAL_CACHE_SORT = "localCacheSort";
    public static final String LEXICOGRAPHICALLY_SORT = "lexicographicallySort";
    public static final String AUTO_COMPLETE_SORT = "autoCompleteSort";
    private static final LocalRepoConfigComparator LOCAL_REPO_CONFIG_COMPARATOR = new LocalRepoConfigComparator();
    private static final Comparator<RepoConfig> LEX_COMPARATOR = Comparator.comparing(RepoConfig::getKey);

    public static Comparator<RepoConfig> getComparator(String sortBy) {
        return switch (sortBy) {
            case LOCAL_CACHE_SORT -> LOCAL_REPO_CONFIG_COMPARATOR;
            case AUTO_COMPLETE_SORT -> new RepoKeyAutoCompleteComparator(null);
            case LEXICOGRAPHICALLY_SORT -> LEX_COMPARATOR;
            default -> LEX_COMPARATOR;
        };
    }

    public static Comparator<RepoConfig> getComparator(String sortBy, String pattern) {
        return switch (sortBy) {
            case LOCAL_CACHE_SORT -> LOCAL_REPO_CONFIG_COMPARATOR;
            case AUTO_COMPLETE_SORT -> new RepoKeyAutoCompleteComparator(pattern);
            case LEXICOGRAPHICALLY_SORT -> LEX_COMPARATOR;
            default -> LEX_COMPARATOR;
        };
    }

    /**
     * Comparator for sorting LocalConfig objects based on their cache status and repo key.
     * LocalConfig objects are sorted first by LocalCacheRepoConfig, and then by their repo key
     * in a case-insensitive manner.
     */
    protected static class LocalRepoConfigComparator implements Comparator<RepoConfig> {

        @Override
        public int compare(RepoConfig o1, RepoConfig o2) {
            // Check if o1 is cached and o2 is not
            if (o1 instanceof LocalConfig localConfig1 && o2 instanceof LocalConfig localConfig2) {
                if (localConfig1.isCache() && !localConfig2.isCache()) {
                    return 1;
                }
                // Check if o1 is not cached and o2 is cached
                else if (!localConfig1.isCache() && localConfig2.isCache()) {
                    return -1;
                }
            }
            return o1.getKey().toLowerCase().compareTo(o2.getKey().toLowerCase());
        }
    }
}
