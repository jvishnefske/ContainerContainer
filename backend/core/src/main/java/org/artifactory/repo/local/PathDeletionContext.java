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

package org.artifactory.repo.local;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.repo.StoringRepo;

/**
 * Object containing method params to use in
 * {@link org.artifactory.repo.StoringRepo#shouldProtectPathDeletion(PathDeletionContext)}
 *
 * @author Shay Yaakov
 */
public class PathDeletionContext {

    private StoringRepo repo;
    private String path;
    private BasicStatusHolder status;
    private boolean assertOverwrite;
    private String requestSha1;
    private String requestSha2;
    private boolean forceExpiryCheck;
    private boolean idempotentDelete;

    public PathDeletionContext(StoringRepo repo, String path, BasicStatusHolder status, boolean assertOverwrite,
            String requestSha1, String requestSha2, boolean forceExpiryCheck, boolean idempotentDelete) {
        this.repo = repo;
        this.path = path;
        this.status = status;
        this.assertOverwrite = assertOverwrite;
        this.requestSha1 = requestSha1;
        this.requestSha2 = requestSha2;
        this.forceExpiryCheck = forceExpiryCheck;
        this.idempotentDelete = idempotentDelete;
    }

    public StoringRepo getRepo() {
        return repo;
    }

    public String getPath() {
        return path;
    }

    public BasicStatusHolder getStatus() {
        return status;
    }

    public boolean isAssertOverwrite() {
        return assertOverwrite;
    }

    public String getRequestSha1() {
        return requestSha1;
    }

    public String getRequestSha2() {
        return requestSha2;
    }

    public boolean isForceExpiryCheck() {
        return forceExpiryCheck;
    }

    /**
     * @return {@code true} if a delete targeting an already-missing artifact should be treated as an idempotent no-op
     * instead of failing with a not-found error.
     */
    public boolean isIdempotentDelete() {
        return idempotentDelete;
    }

    public static class Builder {

        private StoringRepo repo;
        private String path;
        private boolean assertOverwrite;
        private String requestSha1;
        private String requestSha2;
        private BasicStatusHolder status;
        private boolean forceExpiryCheck;
        private boolean idempotentDelete;

        public Builder(StoringRepo repo, String path, BasicStatusHolder status) {
            this.repo = repo;
            this.path = path;
            this.status = status;
        }

        public Builder(StoringRepo repo, String path) {
            this.repo = repo;
            this.path = path;
            this.status = new BasicStatusHolder();
        }

        public Builder assertOverwrite(boolean assertOverwrite) {
            this.assertOverwrite = assertOverwrite;
            return this;
        }

        public Builder requestSha1(String requestSha1) {
            this.requestSha1 = requestSha1;
            return this;
        }

        public Builder requestSha2(String requestSha2) {
            this.requestSha2 = requestSha2;
            return this;
        }

        public Builder forceExpiryCheck(boolean forceExpiryCheck) {
            this.forceExpiryCheck = forceExpiryCheck;
            return this;
        }

        public Builder idempotentDelete(boolean idempotentDelete) {
            this.idempotentDelete = idempotentDelete;
            return this;
        }

        public PathDeletionContext build() {
            return new PathDeletionContext(repo, path, status, assertOverwrite, requestSha1, requestSha2,
                    forceExpiryCheck, idempotentDelete);
        }
    }
}
