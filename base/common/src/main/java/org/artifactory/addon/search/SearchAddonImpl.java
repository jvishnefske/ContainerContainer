/*
 *
 * Copyright 2018 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.addon.search;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.artifactory.api.search.SearchService;
import org.artifactory.api.search.stats.StatsSearchResult;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.stream.Stream;

/**
 * Full implementation of the search addon.
 *
 * @author Yossi Shaul
 */
@Component
public class SearchAddonImpl implements ArtifactSearchAddon {

    protected final SearchService searchService;

    @Autowired
    public SearchAddonImpl(@NonNull SearchService searchService){
        this.searchService = searchService;
    }

    @Override
    public Stream<StatsSearchResult> streamStatsForArtifactsNotDownloadedSince(@Nullable Calendar since,
            @Nullable Calendar createdBefore, String... repositories) {
        return searchService.streamStatsForArtifactsNotDownloadedSince(since, createdBefore, repositories);
    }

    @Override
    public Stream<RepoPath> streamArtifactsRepoPathNotDownloadedSince(@Nullable Calendar since,
            @Nullable Calendar createdBefore, String... repositories) {
        return searchService.streamArtifactsRepoPathNotDownloadedSince(since, createdBefore, repositories);
    }

    @Override
    public Stream<ItemInfo> streamArtifactsItemInfoNotDownloadedSince(@Nullable Calendar since,
            @Nullable Calendar createdBefore, Integer limit, boolean sortByUpdated, String... repositories) {
        return searchService.streamArtifactsItemInfoNotDownloadedSince(since, createdBefore, limit, sortByUpdated,
                repositories);
    }

    @Override
    public boolean isDefault() {
        return true;
    }
}
