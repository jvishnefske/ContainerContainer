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

package org.artifactory.api.rest.search.result;

import com.google.common.collect.Sets;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.checksum.ChecksumType;

import java.util.Set;

/**
 * Search result object to be returned by REST bad checksum searches
 *
 * @author Tomer Cohen
 */
@Data
@NoArgsConstructor
public class BadChecksumRestSearchResult {

    private Set<SearchEntry> results = Sets.newHashSet();
    private boolean limitReached;

    public void addResultAccordingToType(String uri, String clientChecksum, String serverChecksum, ChecksumType type) {
        results.add(SearchEntry.createSearchEntryAccordingToType(uri, serverChecksum, clientChecksum, type));
    }

    @Setter
    @Getter
    @NoArgsConstructor
    static class SearchEntry {
        private String uri;
        private String serverMd5;
        private String clientMd5;
        private String serverSha1;
        private String clientSha1;
        private String serverSha2;
        private String clientSha2;

        SearchEntry(String uri) {
            this.uri = uri;
        }

        static SearchEntry createSearchEntryAccordingToType(String uri, String serverChecksum, String clientChecksum,
                ChecksumType type) {
            SearchEntry entry = new SearchEntry(uri);
            serverChecksum = StringUtils.isNotBlank(serverChecksum) ? serverChecksum : "";
            clientChecksum = StringUtils.isNotBlank(clientChecksum) ? clientChecksum : "";
            if (ChecksumType.md5.equals(type)) {
                entry.setServerMd5(serverChecksum);
                entry.setClientMd5(clientChecksum);
            } else if (ChecksumType.sha1.equals(type)) {
                entry.setServerSha1(serverChecksum);
                entry.setClientSha1(clientChecksum);
            } else if (ChecksumType.sha256.equals(type)) {
                entry.setServerSha2(serverChecksum);
                entry.setClientSha2(clientChecksum);
            } else {
                throw new AssertionError();
            }
            return entry;
        }
    }
}
