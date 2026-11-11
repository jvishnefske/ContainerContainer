/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.addon.search;

import org.artifactory.common.addon.Addon;
import org.artifactory.api.search.stats.StatsSearchResult;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;

import jakarta.annotation.Nullable;
import java.util.Calendar;
import java.util.stream.Stream;

/**
 * @author Chen Keinan
 */
public interface ArtifactSearchAddon extends Addon {

    Stream<StatsSearchResult> streamStatsForArtifactsNotDownloadedSince(@Nullable Calendar since,
            @Nullable Calendar createdBefore,
            String... repositories);

    Stream<RepoPath> streamArtifactsRepoPathNotDownloadedSince(@Nullable Calendar since,
            @Nullable Calendar createdBefore,
            String... repositories);

    Stream<ItemInfo> streamArtifactsItemInfoNotDownloadedSince(@Nullable Calendar since,
            @Nullable Calendar createdBefore,
            Integer limit,
            boolean sortByUpdated,
            String... repositories);

}
