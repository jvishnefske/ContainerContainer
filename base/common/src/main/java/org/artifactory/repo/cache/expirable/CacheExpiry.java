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

package org.artifactory.repo.cache.expirable;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.spring.ReloadableBean;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * @author Noam Y. Tenne
 * Interface for the service that combine all CacheExpiry checkers together
 */
public interface CacheExpiry extends ReloadableBean {
    boolean isExpirable(RepoType repoType, String repoKey, @Nonnull String path);

    boolean isMetadata(RepoType repoType, String repoKey, @Nonnull String path);

    /**
     * For metadata artifacts, return its type, e.g. repo_index, pkg_info, etc. This method is used during downloads
     * to correctly classify the current REST API request.
     *
     * @return the metadata artifact type (see RestApiGroup constants), or null if the artifact is not metadata.
     */
    String getMetadataArtifactType(@Nonnull RepoType repoType, @Nonnull String repoKey, @Nonnull String path);

    boolean isLocalGenerated(@Nonnull RepoType repoType, @Nonnull String repoKey, @Nonnull String path);

    boolean shouldExcludePropertyFromMirroring(@Nonnull RepoType repoType, @Nonnull String propertyKey);

    boolean shouldExcludePropertyFromMirroring(@Nonnull String repoKey, @Nonnull String propertyKey);

    Set<String> excludePropertyValueFromMirroring(@Nonnull RepoType repoType, @Nonnull String propertyKey,
            Set<String> values);
}
