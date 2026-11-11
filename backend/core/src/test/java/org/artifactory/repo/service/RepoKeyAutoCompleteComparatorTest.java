package org.artifactory.repo.service;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepoKeyAutoCompleteComparatorTest {

    @Test
    void testCompareByLength() {
        RepoKeyAutoCompleteComparator comparator = new RepoKeyAutoCompleteComparator(null);
        RepoConfig repo1 = LocalRepoConfig.create("short", RepoType.Generic);
        RepoConfig repo2 = LocalRepoConfig.create("longer", RepoType.Generic);

        int result = comparator.compare(repo1, repo2);
        assertEquals(-1, result);

        result = comparator.compare(repo2, repo1);
        assertEquals(1, result);

        result = comparator.compare(repo1, repo1);
        assertEquals(0, result);
    }

    @Test
    void testCompareByPatternAndLexicographically() {
        RepoKeyAutoCompleteComparator comparator = new RepoKeyAutoCompleteComparator("pattern");
        RepoConfig repo1 = LocalRepoConfig.create("patternRepo", RepoType.Generic);
        RepoConfig repo2 = LocalRepoConfig.create("anotherRepo", RepoType.Generic);

        int result = comparator.compare(repo1, repo2);
        assertEquals(-1, result);

        result = comparator.compare(repo2, repo1);
        assertEquals(1, result);

        RepoConfig repo3 = LocalRepoConfig.create("patternAnotherRepo", RepoType.Generic);
        result = comparator.compare(repo1, repo3);
        assertEquals(-1, result);

        result = comparator.compare(repo3, repo1);
        assertEquals(1, result);

        RepoConfig repo4 = LocalRepoConfig.create("patternRepo", RepoType.Generic);
        result = comparator.compare(repo1, repo4);
        assertEquals(0, result);
    }

    @Test
    void testCompareWithoutPattern() {
        RepoKeyAutoCompleteComparator comparator = new RepoKeyAutoCompleteComparator(null);
        RepoConfig repo1 = LocalRepoConfig.create("reoA", RepoType.Generic);
        RepoConfig repo2 = LocalRepoConfig.create("repoB", RepoType.Generic);

        int result = comparator.compare(repo1, repo2);
        assertEquals(-1, result);

        result = comparator.compare(repo2, repo1);
        assertEquals(1, result);

        RepoConfig repo3 = LocalRepoConfig.create("reoA", RepoType.Generic);
        result = comparator.compare(repo1, repo3);
        assertEquals(0, result);
    }

    @Test
    void testSortingRepoConfigs() {
        RepoKeyAutoCompleteComparator comparator = new RepoKeyAutoCompleteComparator("");
        List<RepoConfig> repoConfigs = new ArrayList<>();
        repoConfigs.add(LocalRepoConfig.create("c---repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("a-repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("b--repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("c-repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("b-repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("c--repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("a---repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("a--repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("b---repo", RepoType.Generic));

        repoConfigs.sort(comparator);

        List<RepoConfig> expectedOrder = new ArrayList<>();
        expectedOrder.add(LocalRepoConfig.create("a-repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("b-repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("c-repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("a--repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("b--repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("c--repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("a---repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("b---repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("c---repo", RepoType.Generic));
        for (int i = 0; i < repoConfigs.size(); i++) {
            assertEquals(expectedOrder.get(i).getKey(), repoConfigs.get(i).getKey());
        }
    }

    @Test
    void testSortingRepoConfigsWithPattern() {
        RepoKeyAutoCompleteComparator comparator = new RepoKeyAutoCompleteComparator("b");
        List<RepoConfig> repoConfigs = new ArrayList<>();
        repoConfigs.add(LocalRepoConfig.create("c---repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("a-repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("b--repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("c-repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("b-repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("c--repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("a---repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("a--repo", RepoType.Generic));
        repoConfigs.add(LocalRepoConfig.create("b---repo", RepoType.Generic));

        repoConfigs.sort(comparator);

        List<RepoConfig> expectedOrder = new ArrayList<>();
        expectedOrder.add(LocalRepoConfig.create("b-repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("a-repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("c-repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("b--repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("a--repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("c--repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("b---repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("a---repo", RepoType.Generic));
        expectedOrder.add(LocalRepoConfig.create("c---repo", RepoType.Generic));
        for (int i = 0; i < repoConfigs.size(); i++) {
            assertEquals(expectedOrder.get(i).getKey(), repoConfigs.get(i).getKey());
        }
    }
}