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

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.event.EventType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.db.fs.model.DbMutableFile;
import org.artifactory.storage.event.EventServiceBase;
import org.artifactory.storage.event.StorageEventInfo;
import org.artifactory.storage.tx.SessionEventHandler;
import org.artifactory.storage.tx.SessionResource;
import org.artifactory.storage.tx.SessionResourceAdapter;
import org.jfrog.storage.binstore.common.ZeroBytesBinaryElement;

import java.util.Objects;
import java.util.Set;


/**
 * The gc hints session resource is a {@link SessionResource} that receives low level storage events and stores them in
 * the storage_events table once the transaction is committed.
 * Like any other session resource, it is attached to a single thread, lives and dies in the context of the current
 * database transaction.
 *
 * @author TamirH
 */
@Slf4j
public class StorageEventsSessionResource extends SessionResourceAdapter implements SessionEventHandler {

    private final Set<StorageEventInfo> storageEvents = Sets.newLinkedHashSet();
    private boolean shouldTriggerQ = false;

    @Override
    public void beforeCommit() {
        EventServiceBase<StorageEventInfo> eventsService = ContextHelper.get().beanForType(StorageEventsService.class);
        if (!storageEvents.isEmpty()) {
            try {
                eventsService.appendEvents(storageEvents.stream().toList());
                this.shouldTriggerQ = true;
            } catch (Exception e) {
                log.error("Failed to append new storage events {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public void afterConnectionReleased(boolean committed) {
        if (committed && shouldTriggerQ) {
            ContextHelper.get().beanForType(StorageEventsTriggerService.class).scheduleTrigger();
        }
    }

    @Override
    public void itemCreated(VfsItem item) {
        // Not implemented
    }

    @Override
    public void itemUpdated(VfsItem item) {
        if (!ConstantValues.gcEventsCleanupEnabled.getBoolean()) {
            return;
        }
        if (item instanceof DbMutableFile dbMutableFile) {
            addItemInfo(dbMutableFile.getOriginalInfo());
        }
    }

    @Override
    public void itemDeleted(VfsItem item) {
        if (!ConstantValues.gcEventsCleanupEnabled.getBoolean()) {
            return;
        }
        addItemInfo(item.getInfo());
    }

    private void addItemInfo(ItemInfo itemInfo) {
        if (itemInfo instanceof FileInfo info) {
            final ChecksumsInfo checksumsInfo = info.getChecksumsInfo();
            final String sha1 = checksumsInfo == null ? null : checksumsInfo.getSha1();
            if (sha1 != null) {
                if (!ZeroBytesBinaryElement.isZeroBytesSha1(sha1)) {
                    String sha256 = Objects.requireNonNullElse(checksumsInfo.getSha256(), "");
                    storageEvents.add(new StorageEventInfo(EventType.delete, sha1, sha256, info.getSize()));
                } else {
                    log.trace("Event of zero-length item won't be processed");
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Missing checksums for {}, sha1: {}", itemInfo.getRelPath(), info.getSha1());
                }
            }
        }
    }

    @Override
    public void propertiesModified(VfsItem item) {
        // Not implemented
    }
}
