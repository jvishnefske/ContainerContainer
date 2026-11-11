package org.artifactory.repo.service;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.model.RepoConfig;

import java.util.Comparator;

/**
 * Comparator for comparing {@link RepoConfig} objects based on their repository keys.
 * The comparison is done first by the length of the repository keys, and if they are of equal length,
 * by whether they start with a specified pattern and lexicographically.
 */
@AllArgsConstructor
public class RepoKeyAutoCompleteComparator implements Comparator<RepoConfig> {
    private String patternStr;

    @Override
    public int compare(RepoConfig o1, RepoConfig o2) {
        String repoKey1 = o1.getKey();
        String repoKey2 = o2.getKey();

        int lengthComparison = compareByLength(repoKey1, repoKey2);
        if (lengthComparison != 0) {
            return lengthComparison;
        }
        // If the length of the repository keys is equal, compare them by pattern and lexicographically
        return compareByPatternAndLexicographically(repoKey1, repoKey2);
    }

    private int compareByLength(String repoKey1, String repoKey2) {
        return Integer.compare(repoKey1.length(), repoKey2.length());
    }

    private int compareByPatternAndLexicographically(String repoKey1, String repoKey2) {
        if (StringUtils.isBlank(patternStr)) {
            return repoKey1.compareTo(repoKey2);
        }
        boolean startsWithPattern1 = repoKey1.startsWith(patternStr);
        boolean startsWithPattern2 = repoKey2.startsWith(patternStr);

        if (startsWithPattern1 && startsWithPattern2) {
            return repoKey1.compareTo(repoKey2);    // if both start with the pattern, compare them lexicographically
        } else if (!startsWithPattern1 && !startsWithPattern2) {
            return repoKey1.compareTo(repoKey2); // if neither starts with the pattern, compare them lexicographically
        } else if (startsWithPattern1) {
            return -1;  // if only the first key starts with the pattern, it should come first
        } else {
            return 1;   // then the second key starts with the pattern, it should come first
        }
    }
}