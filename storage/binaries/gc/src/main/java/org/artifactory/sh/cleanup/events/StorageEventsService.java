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

package org.artifactory.sh.cleanup.events;

import org.artifactory.storage.event.StorageEventInfo;
import org.artifactory.storage.event.EventServiceBase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A business service interface to interact with the tree events.
 *
 * @author TamirH
 */
public interface StorageEventsService extends EventServiceBase<StorageEventInfo> {
    @Transactional
    void deleteHintsBySha(List<String> shaToDelete);

    @Transactional
    void deleteByCursor(long cursorId);

    int deleteAllPreviousBySha1(String sha1);

    /**
     * Get the latest events for the given SHA1s
     * @param sha1s the SHA1s to get the latest events for
     * @return  the latest events for the given SHA1s or an empty list if no events were found
     */
    List<StorageEventInfo> getLatestEventsBySHA1(List<String> sha1s);
}
