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

package org.artifactory.descriptor.repo;

import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

/**
 * @author yoavl
 */
@GenerateDiffFunction(defaultImpl = RepoBaseDescriptor.class)
public interface RepoDescriptor extends Descriptor, Comparable {
    String getKey();

    RepoType getType();

    String getDescription();

    String getNotes();

    /**
     * @return A comma separated list of artifact patterns to include when evaluating requests.
     */
    String getIncludesPattern();

    /**
     * @return A comma separated list of artifact patterns to exclude when evaluating requests.
     */
    String getExcludesPattern();

    /**
     * @return The time to live for sign url in seconds.
     */
    long getSignedUrlTtl();

    /**
     * @return True if not a virtual repository (for example remote cached or local)
     */
    boolean isReal();

    RepoLayout getRepoLayout();

    KeyPairDescriptor getPrimaryKeyPairDescriptor();

    KeyPairDescriptor getSecondaryKeyPairDescriptor();

    boolean isMavenRepoLayout();

    DockerApiVersion getDockerApiVersion();

    boolean isForceNugetAuthentication();

    /**
     * If new repo needs forceAuthentication flag it's better not to add new one but to rename
     * isForceNugetAuthentication to isForceAuthentication and use for desired package type. After planned centralConfig
     * refactoring efforts for renaming should be minimal.
     */
    boolean isForceConanAuthentication();

    boolean isForceP2Authentication();

    boolean isDdebSupported();

    /**
     * Use internal, not git indexing for Cargo remote repositories.
     * See <a href="https://github.com/rust-lang/cargo/issues/9069">Sparse Index</a>
     */
    boolean isCargoInternalIndex();

    default boolean isDownloadRedirect() {
        return false;
    }

    default boolean isCdnRedirect() {
        return false;
    }
}