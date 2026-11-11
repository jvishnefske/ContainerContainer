package org.artifactory.sh.cleanup.events;

import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.fs.VfsItemEvent;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.artifactory.storage.tx.SessionEventHandler;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * listener delegator class to delegate events coming from artifactory storage db module into gc module
 * @author Avishay Halpren
 */
@Component
public class StorageEventsVfsItemListener implements ApplicationListener<VfsItemEvent> {

    @Override
    public void onApplicationEvent(VfsItemEvent event) {
        SessionEventHandler sessionResource = getSessionResource();
        if (event.getType().equals(VfsItemEvent.Type.update)) {
            sessionResource.itemUpdated((VfsItem) event.getSource());
        }
        if (event.getType().equals(VfsItemEvent.Type.delete)) {
            sessionResource.itemDeleted((VfsItem) event.getSource());
        }
    }

    private SessionEventHandler getSessionResource() {
        return StorageSessionHolder.getSession().getOrCreateResource(StorageEventsSessionResource.class);
    }
}
