package org.artifactory.sh.config;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.event.queue.QueueTriggeringService;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.sh.cleanup.GCService;
import org.artifactory.sh.cleanup.GCServiceImpl;
import org.artifactory.sh.cleanup.GarbageCollectionDatastoreService;
import org.artifactory.sh.cleanup.events.StorageEventsQueueConfigurationRegistrar;
import org.artifactory.sh.cleanup.events.StorageEventsService;
import org.artifactory.sh.cleanup.events.StorageEventsServiceImpl;
import org.artifactory.sh.cleanup.events.StorageEventsTriggerService;
import org.artifactory.sh.cleanup.events.StorageEventsTriggerServiceImpl;
import org.artifactory.sh.cleanup.sync.BinariesSyncEventListener;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.event.dao.StorageEventsDao;
import org.artifactory.storage.db.fs.dao.BinariesGcDao;
import org.artifactory.storage.fs.service.ConfigsService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ArtifactoryStorageHandlerGCConfig {

    public ArtifactoryStorageHandlerGCConfig() {
        log.info("injecting spring beans for storage-handler-gc module");
    }

    @Bean
    GarbageCollectionDatastoreService garbageCollectionDatastoreService() {
        return new GarbageCollectionDatastoreService();
    }

    @Bean
    GCService gcService(ArtifactoryBinaryService binaryService,
            TrashService trashService, GarbageCollectionDatastoreService datastoreService,
            ConfigsService configsService,
            BinariesGcDao binariesGcDao, ApplicationEventPublisher eventPublisher,
            RepositoryService repoService, SecurityService secService, DbService dbService,
            StorageService storageService) {
        return new GCServiceImpl(binaryService, trashService, datastoreService,
                configsService, binariesGcDao, eventPublisher, repoService, secService,
                dbService, storageService);
    }

    @Bean
    StorageEventsService storageEventsService(StorageEventsDao storageEventsDao) {
        return new StorageEventsServiceImpl(storageEventsDao);
    }

    @Bean
    StorageEventsTriggerService storageEventsTriggerService(QueueTriggeringService queueTriggeringService) {
        return new StorageEventsTriggerServiceImpl(queueTriggeringService);
    }

    @Bean
    StorageEventsQueueConfigurationRegistrar storageEventsRegistrar(
            GCService gcService, ApplicationEventPublisher eventPublisher,
            StorageEventsService storageEvents, RepositoryService repoService) {
        return new StorageEventsQueueConfigurationRegistrar(gcService, eventPublisher, storageEvents, repoService);
    }

    @Bean
    BinariesSyncEventListener binariesSyncEventListener(RepositoryService repoService,
            StorageEventsService storageEventsService) {
        return new BinariesSyncEventListener(repoService, storageEventsService);
    }
}
