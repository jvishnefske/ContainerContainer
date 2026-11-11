package org.artifactory.sh.cleanup.sync;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.event.EventType;
import org.artifactory.sh.cleanup.events.StorageEventsService;
import org.artifactory.storage.event.StorageEventInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.jfrog.storage.events.BinariesSyncEvent;
import org.springframework.context.ApplicationListener;

import java.util.ArrayList;
import java.util.List;

/**
 * @author IdanD
 */
@Slf4j
public class BinariesSyncEventListener implements ApplicationListener<BinariesSyncEvent> {

    private final RepositoryService repositoryService;
    private final StorageEventsService storageEventsService;

    public BinariesSyncEventListener(RepositoryService repositoryService, StorageEventsService storageEventsService) {
        this.repositoryService = repositoryService;
        this.storageEventsService = storageEventsService;
    }

    @Override
    public void onApplicationEvent(BinariesSyncEvent event) {
        deleteMpuBinariesAsync(event.getDeletedBinaries());
    }

    private void deleteMpuBinariesAsync(List<BinaryElement> binaryElements) {
        log.debug("Starting to public delete events after multipart upload cleanup");
        List<StorageEventInfo> storageEventInfos = new ArrayList<>();
        for (BinaryElement element : binaryElements) {
            if (!repositoryService.existsBySha1(element.getSha1())) {
                StorageEventInfo.Data data = StorageEventInfo.Data.empty();
                data.setBinarySync(true);
                storageEventInfos.add(
                        new StorageEventInfo(
                                EventType.delete, element.getSha1(), element.getSha2(), element.getLength(), data));
            }
        }
        storageEventsService.appendEvents(storageEventInfos);
    }
}
