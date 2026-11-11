package org.artifactory.queue.register;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.EnumUtils;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.repo.PackageType;
import org.artifactory.common.repo.RepoType;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueConfigurationRegistrar;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueCursorUtils;
import org.artifactory.event.queue.QueueEventImplementor;
import org.artifactory.event.queue.QueueOperations;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.queue.QueueCursorTypeMapper;
import org.artifactory.queue.services.GlobalPersistentQueueImpl;
import org.artifactory.queue.services.PersistentQueue;
import org.artifactory.queue.services.PersistentQueueCursorService;
import org.artifactory.queue.services.PersistentQueueImpl;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoConfigReload;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.storage.db.event.service.NodeEventCursorService;
import org.artifactory.storage.event.EventsService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * The registration service is responsible to create and remove all PersistentQueue's instances.
 * an instance of PersistentQueue is being created for each repoKey+cursorType+variant<br>
 * e.g:<br>
 * repoNames = my-local-maven1 ,my-local-maven2 ,my-virtual-maven1
 * cursorType = Incremental-index, replication(var1,var2)
 * will create this 9 PersistentQueues instances:
 * -  my-local-maven1-incremental-index-queue
 * -  my-local-maven2-incremental-index-queue
 * -  my-virtual-maven1-incremental-index-queue
 * -  my-local-maven1-replication-var1-queue
 * -  my-local-maven1-replication-var2-queue
 * -  my-local-maven2-replication-var1-queue
 * -  my-local-maven2-replication-var2-queue
 * -  my-virtual-maven1-replication-var1--queue
 * -  my-virtual-maven1-replication-var2--queue
 *
 * The creation process is being done on start up (init method) and whenever a new repository is added
 * (registerRepository method) - by collecting all QueueConfigurationRegistrar implementations.
 * and by application code using the QueueService.register method which accept a single
 * QueueConfiguration without the need to create a Registrar implementation.
 *
 * the removal of a queue is being done whenever a repository is deleted (deRegisterRepository method)
 *
 * @author Avishay Halpren
 */
@Slf4j
@Component
@Reloadable(beanClass = PersistentQueueRegistrationService.class, initAfter = {InternalRepositoryService.class},
        listenOn = {CentralConfigKey.none})
public class PersistentQueueRegistrationServiceImpl implements PersistentQueueRegistrationService {

    /**
     * Stores all the queues created by this service.
     * the key is repoKey and the value is a map of repoKey + cursorType for multiple (variant based) queues
     */
    private final Map<String, Multimap<RepoAndCursor, PersistentQueue>> queuesPerRepo = new ConcurrentHashMap<>();
    /**
     * Queues that no matter what, need to be triggered.
     * Not depending on specific repo operation.
     */
    private final Set<PersistentQueue> alwaysTriggerQueues = Sets.newHashSet();
    /**
     * this lock defends modifications of queues multimap which is not thread safe.
     */
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    private final List<QueueConfigurationRegistrar> registrars;
    private final List<GlobalQueueConfigurationRegistrar> globalRegistrars;
    private final RepositoryService repositoryService;
    private final PersistentQueueCursorService persistentQueueCursorService;
    private final EventsService eventsService;
    private final NodeEventCursorService nodeEventCursorService;

    public PersistentQueueRegistrationServiceImpl(List<QueueConfigurationRegistrar> registrars,
            List<GlobalQueueConfigurationRegistrar> globalPersistentQueues, RepositoryService repositoryService,
            PersistentQueueCursorService persistentQueueCursorService, EventsService eventsService,
            NodeEventCursorService nodeEventCursorService) {
        this.registrars = registrars.stream().filter(QueueConfigurationRegistrar::shouldBeRegistered).toList();
        this.globalRegistrars =
                globalPersistentQueues.stream().filter(GlobalQueueConfigurationRegistrar::shouldRegister).toList();
        this.repositoryService = repositoryService;
        this.persistentQueueCursorService = persistentQueueCursorService;
        this.eventsService = eventsService;
        this.nodeEventCursorService = nodeEventCursorService;
    }

    @Override
    public void init() {
        //register all existing repositories on startup
        repositoryService.getAllRepoKeys().forEach(repoKey -> registerRepository(repoKey, false));
        registrars.stream().filter(reg -> !reg.isRepoBaseRegistrar())
                .forEach(reg -> createQueueIfNeeded(reg.getOverrideQueueName(), false,
                        reg.getConfigurations("").get(0)));
        globalRegistrars.forEach(this::createGlobalQueue);
    }

    @Override
    public void onRepoConfigReload(Map<RepoDetailsType, List<RepoConfigReload>> repoConfigReload) {
        //This method does not support update.
        // new repos
        repoConfigReload.values().stream()
                .flatMap(Collection::stream)
                .filter(change -> change.getConfigAction().equals(RepoConfigChange.ConfigAction.REPO_CREATE))
                .forEach(change -> registerRepository(change.getRepoKey(), true));

        // deleted repos
        repoConfigReload.values().stream()
                .flatMap(Collection::stream)
                .filter(change -> change.getConfigAction().equals(RepoConfigChange.ConfigAction.REPO_DELETE))
                .forEach(change -> deRegisterRepository(change.getRepoKey()));
    }

    @Override
    public void register(String repoKey, QueueConfiguration configuration) {
        createQueue(repoKey, false, configuration);
    }

    @Override
    public void register(String repoKey, QueueConfiguration configuration, PersistentQueue persistentQueue) {
        createQueue(repoKey, false, persistentQueue);
    }

    @Override
    public void registerRepository(String repoKey) {
        registerRepository(repoKey, true);
    }

    /**
     * This method is called in the init phase for all active repositories and when creating a new repository.
     *
     * @param newRepository determine if registering an existing repository or a new one
     */
    private void registerRepository(String repoKey, boolean newRepository) {
        if (registrars.isEmpty()) {
            //nothing to do if no registrars
            return;
        }
        RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repoKey);

        if (repoConfig == null) {
            return;
        }
        final String finalRepoKey = mutateRemoteRepoKeyToCacheRepoKeyIfNeeded(repoKey, repoConfig);
        if (finalRepoKey == null) {
            return;
        }
        Set<RepoType> repoTypes = getRepoTypes(repoConfig);
        if (!Objects.isNull(repoTypes)) {
            PackageType packageType = getPackageType(repoConfig);
            //collect all configurations for a repoKey
            List<QueueConfiguration> configurations = registrars.stream()
                    .filter(QueueConfigurationRegistrar::isRepoBaseRegistrar)
                    .filter(registrar -> shouldCreateQueue(packageType, repoTypes, registrar))
                    .map(registrar -> registrar.getConfigurations(finalRepoKey))
                    .flatMap(Collection::stream).toList();
            for (QueueConfiguration queueConfiguration : configurations) {
                createQueueIfNeeded(finalRepoKey, newRepository, queueConfiguration);
            }
        }
    }

    /**
     * In case of remote repository, we want to register only the cache repository if exists
     */
    @Nullable
    private static String mutateRemoteRepoKeyToCacheRepoKeyIfNeeded(String currentRepoKey, RepoConfig repoConfig) {
        if (repoConfig instanceof RemoteRepoConfig remoteRepoConfig) {
            if (remoteRepoConfig.getRepoTypeConfig().isStoreArtifactsLocally()) {
                currentRepoKey += LocalCacheRepoDescriptor.PATH_SUFFIX;
            } else {
                return null;
            }
        }
        return currentRepoKey;
    }

    private void createQueueIfNeeded(String key, boolean isNewRepository, QueueConfiguration queueConfiguration) {
        try {
            if (!isRegistered(key, queueConfiguration.getCursorType(), queueConfiguration.getVariant())) {
                createQueue(key, isNewRepository, queueConfiguration);
            }
        } catch (Exception e) {
            log.error("An error occurred while trying to register key '{}' to queue: {}", key,
                    e.getMessage(), e);
        }
    }

    private void createGlobalQueue(GlobalQueueConfigurationRegistrar registrar) {
        QueueConfiguration queueConfiguration = registrar.getQueueConfiguration();
        String queueName = registrar.getQueueName();
        PersistentQueue globalPersistentQueue = new GlobalPersistentQueueImpl(queueConfiguration, queueName);
        createQueue(queueName, false, globalPersistentQueue);
    }

    @VisibleForTesting
    public void createQueue(String repoKey, boolean isNewRepository, QueueConfiguration queueConfiguration) {
        //create the new queue and register it
        PersistentQueue persistentQueue = new PersistentQueueImpl(queueConfiguration, repoKey);
        createQueue(repoKey, isNewRepository, persistentQueue);
    }

    @VisibleForTesting
    void createQueue(String repoKey, boolean isNewRepository, PersistentQueue persistentQueue) {
        QueueConfiguration queueConfiguration = persistentQueue.getQueueConfiguration();
        QueueCursorType cursorType = queueConfiguration.getCursorType();
        try {
            readWriteLock.writeLock().lock();
            RepoAndCursor key = getRepoAndCursor(repoKey, cursorType, persistentQueue);
            // Create the marker
            boolean markerCreated = persistentQueue.createMarkerIfNotExists();
            if ((isNewRepository || queueConfiguration.isResetEventMarkerOnCreation()) && markerCreated) {
                //whenever a queue for a new repository is created we want to make sure to reset any old marker if
                //exist. The reset method takes care of the cursor and set it to the last event of the queue.
                //This can prevent problems if the original reset(true) call for this queue failed during triggered
                //if we don't do this we could start a new queue with leftover events of a previous queue with same
                //name.
                if (isNewRepository) {
                    //for new repository - get the latest event from DB
                    persistentQueue.reset();
                } else {
                    //for exists repository - get the latest event from cache, as it's relevant only during startup
                    persistentQueue.reset(repoKey, persistentQueueCursorService.getCachedLatestEventId());
                }
            }
            // Store the qq
            Multimap<RepoAndCursor, PersistentQueue> queues =
                    queuesPerRepo.computeIfAbsent(repoKey,
                            k -> Multimaps.synchronizedListMultimap(ArrayListMultimap.create()));
            queues.put(key, persistentQueue);
            if (persistentQueue.shouldAlwaysTrigger()) {
                alwaysTriggerQueues.add(persistentQueue);
            }
        } finally {
            readWriteLock.writeLock().unlock();
        }
        log.debug("Queue created, name: {}, repo: {}", persistentQueue.getName(), repoKey);
    }

    /**
     * Check if the given repository is matching to the configuration requirements for queue creation.
     */
    private boolean shouldCreateQueue(PackageType packageType, Set<RepoType> repoTypes,
            QueueConfigurationRegistrar configuration) {

        boolean packageTypeMatch =
                Objects.isNull(configuration.getPackageType()) ||
                        Objects.equals(configuration.getPackageType(), packageType);

        boolean repoTypeMatch = CollectionUtils.isEmpty(configuration.getRepoTypes())
                || configuration.getRepoTypes().containsAll(repoTypes);

        return packageTypeMatch && repoTypeMatch;
    }

    private PackageType getPackageType(RepoConfig repoConfig) {
        try {
            return PackageType.valueOf(repoConfig.getPackageType().getType().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private Set<RepoType> getRepoTypes(RepoConfig repoConfig) {
        if (repoConfig instanceof LocalCacheRepoConfig) {
            return Set.of(RepoType.REMOTE, RepoType.LOCAL);
        } else if (repoConfig instanceof LocalConfig) {
            return Set.of(RepoType.LOCAL);
        } else if (repoConfig instanceof RemoteRepoConfig) {
            return Set.of(RepoType.REMOTE);
        } else if (repoConfig instanceof VirtualRepoConfig) {
            return Set.of(RepoType.VIRTUAL);
        }
        return null;
    }

    @Override
    public void deRegister(String repoKey, QueueCursorType type, String variant) {
        findQueue(repoKey, type, variant).ifPresentOrElse(queue -> {
            try {
                readWriteLock.writeLock().lock();
                Multimap<RepoAndCursor, PersistentQueue> repoAndCursorPersistentQueueMultimap =
                        queuesPerRepo.get(repoKey);
                if (repoAndCursorPersistentQueueMultimap != null) {
                    repoAndCursorPersistentQueueMultimap.remove(new RepoAndCursor(repoKey, type), queue);
                }
                alwaysTriggerQueues.remove(queue);
                log.info("Queue removed, name: {}, repo: {}", queue.getName(), repoKey);
            } finally {
                readWriteLock.writeLock().unlock();
            }
            queue.shutdown();
            queue.reset(true);
        }, () -> log.debug("Cannot found a queue for {} {} {}", repoKey, type, variant));
    }

    @Override
    public void deRegisterRepository(String repoKey) {
        //this method will be called when deleting a repository
        log.debug("Removing all queues for repo {}", repoKey);
        List<PersistentQueue> removedQueues = null;
        try {
            readWriteLock.writeLock().lock();
            //for each relevant log types try to remove from the map
            //collect the removed queues
            Multimap<RepoAndCursor, PersistentQueue> removed = queuesPerRepo.remove(repoKey);
            if (removed != null && !removed.isEmpty()) {
                removedQueues = removed.values().stream().toList();
                removedQueues.forEach(alwaysTriggerQueues::remove);
            }
        } finally {
            readWriteLock.writeLock().unlock();
        }
        if (removedQueues != null && !removedQueues.isEmpty()) {
            log.info("Unregistering {} queues for repo {}", removedQueues.size(), repoKey);
            //call shutdown on all removed queues - signals all jobs to stop
            removedQueues.forEach(PersistentQueue::shutdown);
            //call the reset on all removed queues - make sure marker is deleted
            //deleting the marker will allow the events cleanup job to do his work.
            removedQueues.forEach(queue -> queue.reset(true));
        } else {
            log.debug("No queues found for {} ", repoKey);
        }
    }

    @Override
    public Collection<PersistentQueue> getQueues(String repoKey, QueueCursorType cursorType) {
        try {
            readWriteLock.readLock().lock();
            //Wrap the result in a new List to avoid concurrent modification exception
            Multimap<RepoAndCursor, PersistentQueue> repoAndCursorPersistentQueueMultimap = queuesPerRepo.get(repoKey);
            if (repoAndCursorPersistentQueueMultimap != null) {
                return Lists.newArrayList(
                        repoAndCursorPersistentQueueMultimap.get(new RepoAndCursor(repoKey, cursorType)));
            }
        } finally {
            readWriteLock.readLock().unlock();
        }
        return Lists.newArrayList();
    }

    @Override
    public Collection<PersistentQueue> getQueues(Set<String> reposKey) {
        try {
            readWriteLock.readLock().lock();
            return reposKey
                    .stream()
                    .map(queuesPerRepo::get)
                    .filter(Objects::nonNull)
                    .flatMap(queues -> queues.values().stream())
                    .collect(Collectors.toSet());
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public Collection<PersistentQueue> getAllQueues() {
        try {
            readWriteLock.readLock().lock();
            return queuesPerRepo.values()
                    .stream()
                    .filter(Objects::nonNull)
                    .flatMap(queues -> queues.values().stream())
                    .collect(Collectors.toSet());
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public boolean isRegistered(String repoKey, QueueCursorType type, String variant) {
        return findQueue(repoKey, type, variant).isPresent();
    }

    @Override
    public void resetQueueIfExist(String repoKey, QueueCursorType type, String variant) {
        try {
            if (isRegistered(repoKey, type, variant)) {
                getQueueOperations(repoKey, type, variant).reset();
            }
        } catch (Exception e) {
            log.error("Failed to reset queue for repo {} with an error: {}", repoKey, e.getMessage(), e);
        }
    }

    @Override
    public QueueOperations getQueueOperations(String repoKey, QueueCursorType type, String variant) {
        return findQueue(repoKey, type, variant).orElseThrow(() -> new RuntimeException(
                String.format("Couldn't find Queue for %s %s %s", type, repoKey, variant)));
    }


    /**
     * created new queue with for different variant with same configuration of the old queue
     *
     * @param repoKey         the repo key that the queue belongs to.
     * @param oldVariant      the variant of the queue we want to copy
     * @param newVariant      the variant of the queue we want to create
     * @param queueCursorType the type of the queue we want to copy
     */
    @Override
    public void copyQueue(String repoKey, String oldVariant, String newVariant, QueueCursorType queueCursorType,
            QueueEventImplementor implementor) {
        boolean registered = isRegistered(repoKey,
                queueCursorType, newVariant);
        if (registered) {
            log.warn("The queue already created for repoKey {}, remote member {}",
                    repoKey, newVariant);
            return;
        }
        log.debug("Found unregistered member: {} staring to copy queue",
                newVariant);
        // copy queue
        PersistentQueue persistentQueue =
                copyPersistentQueue(repoKey, oldVariant, newVariant, queueCursorType, implementor);
        // get the identifier for the queue in the queues map
        RepoAndCursor key = getRepoAndCursor(repoKey, queueCursorType, persistentQueue);
        // copy cursor
        String oldVariantCursor = copyCursor(repoKey, oldVariant, queueCursorType, persistentQueue);

        // store the queue
        queuesPerRepo.computeIfAbsent(repoKey, k -> Multimaps.synchronizedListMultimap(ArrayListMultimap.create()))
                .put(key, persistentQueue);
        if (persistentQueue.shouldAlwaysTrigger()) {
            alwaysTriggerQueues.add(persistentQueue);
        }
        log.debug("Copy queue successfully, new queue created with name: {}, repo: {}," +
                " marker from existing queue name {} ", persistentQueue.getName(), repoKey, oldVariantCursor);
    }

    @Nonnull
    private String copyCursor(String repoKey, String oldVariant, QueueCursorType queueCursorType,
            PersistentQueue persistentQueue) {
        String oldVariantCursor =
                QueueCursorUtils.getCursorName(repoKey, queueCursorType, oldVariant);

        // create the cursor in the DB with the marker of the old marker
        persistentQueue.copyCursor(oldVariantCursor, queueCursorType);
        return oldVariantCursor;
    }

    @Nonnull
    private PersistentQueue copyPersistentQueue(String repoKey, String oldVariant, String newVariant,
            QueueCursorType queueCursorType, QueueEventImplementor implementor) {
        // get the old queue configuration
        QueueConfiguration queueConfiguration = getQueueConfigurationOfOldVariant(repoKey, oldVariant, queueCursorType);

        // copy old variant configuration
        queueConfiguration = copyBuildConfigurationWithVariant(newVariant, implementor, queueConfiguration);

        // create the new persistentQueue class with the configuration of the old queue
        PersistentQueue persistentQueue = new PersistentQueueImpl(queueConfiguration, repoKey);
        log.debug("Queue successfully copied from variant {} to variant {} with the name: {}",
                oldVariant, newVariant, persistentQueue.getName());
        return persistentQueue;
    }

    QueueConfiguration copyBuildConfigurationWithVariant(String newVariant,
            QueueEventImplementor implementor, QueueConfiguration queueConfiguration) {
        return QueueConfiguration.builder()
                .name(queueConfiguration.getName())
                .triggerDecorator(queueConfiguration.getTriggerDecorator())
                .cursorType(queueConfiguration.getCursorType())
                .variant(newVariant)
                .reductionBehaviours(queueConfiguration.getReductionBehaviours())
                .filter(queueConfiguration.getFilter())
                .implementor(implementor)
                .aggregatorConfig(queueConfiguration.getAggregatorConfig())
                .backoffSupplier(queueConfiguration.getBackoffSupplier())
                .enableErrorSupport(queueConfiguration.isEnableErrorSupport())
                .fileSuffix(queueConfiguration.getFileSuffix())
                .maxBulkSize(queueConfiguration.getMaxBulkSize())
                .resetEventMarkerOnCreation(queueConfiguration.isResetEventMarkerOnCreation())
                .maxErrorRetires(queueConfiguration.getMaxErrorRetires())
                .errorOverflowLimit(queueConfiguration.getErrorOverflowLimit())
                .build();
    }

    QueueConfiguration getQueueConfigurationOfOldVariant(String repoKey, String oldVariant,
            QueueCursorType queueCursorType) {
        Collection<PersistentQueue> persistentQueues = queuesPerRepo.get(repoKey)
                .get(new RepoAndCursor(repoKey, queueCursorType));
        Optional<PersistentQueue> oldVariantQueue =
                persistentQueues.stream().filter(q -> q.getQueueConfiguration().getVariant().equals(oldVariant))
                        .findFirst();
        if (oldVariantQueue.isEmpty()) {
            log.warn("Copy queue failed, Cant find queue with repoKey {}, remote member {}",
                    repoKey, oldVariant);
            throw new IllegalStateException(String.format("Copy queue failed, the queue already" +
                            " created for repoKey %s, remote member %s",
                    repoKey, oldVariant));
        }
        log.debug("Found old member to copy: {} staring to copy queue",
                oldVariant);
        return oldVariantQueue.get().getQueueConfiguration();
    }

    @Nonnull
    private RepoAndCursor getRepoAndCursor(String repoKey, QueueCursorType cursorType,
            PersistentQueue persistentQueue) {
        RepoAndCursor key = new RepoAndCursor(repoKey, cursorType);
        //before inserting to the queue - check we dont have the same name already:
        //can't have multiple queues with same name
        //e.g: only 1 ("my-local-maven-repo",IncrementalIndex) queue
        //if we want more than 1 we must use different variants
        //e.g: ("my-local-maven-repo",Replication,var1) queue
        //e.g: ("my-local-maven-repo",Replication,var2) queue
        //this is because a repoKey a variant and a cursorType defines the name of the cursor in the queue.
        //we cannot have 2 different queues working on the same cursor!
        Multimap<RepoAndCursor, PersistentQueue> queues = queuesPerRepo.get(repoKey);
        if (queues != null && queues.get(key).stream().anyMatch(q -> q.getName().equals(persistentQueue.getName()))) {
            throw new IllegalStateException(
                    String.format("found queue with same name %s", persistentQueue.getName()));
        }
        return key;
    }

    @Override
    public Optional<PersistentQueue> findQueue(String repoKey, QueueCursorType type, String variant) {
        try {
            readWriteLock.readLock().lock();
            Multimap<RepoAndCursor, PersistentQueue> queues = queuesPerRepo.get(repoKey);
            if (queues == null) {
                return Optional.empty();
            }
            Collection<PersistentQueue> persistentQueues = queues.get(new RepoAndCursor(repoKey, type));
            return persistentQueues.stream()
                    .filter(q -> q.getQueueConfiguration().getVariant().equals(variant))
                    .findFirst();
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public Collection<PersistentQueue> getConstantQueues() {
        try {
            readWriteLock.readLock().lock();
            return alwaysTriggerQueues;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public NodeEventCursorStatus getConstantQueueState(String cursorType) {
        if (!EnumUtils.isValidEnum(QueueCursorType.class, cursorType.toUpperCase())) {
            throw new BadRequestException("Provided cursor type is not valid");
        }

        QueueCursorType requestedCursorType = QueueCursorType.valueOf(cursorType.toUpperCase());
        try {
            readWriteLock.readLock().lock();
            PersistentQueue foundPersistentQueue = getPersistentQueue(requestedCursorType);
            return foundPersistentQueue.getCurrentMarkerCursorForQueue().getStatus();
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public void updateConstantQueue(QueueCursorType cursorType, NodeEventCursorStatus status, long timeToReset) {
        try {
            readWriteLock.readLock().lock();
            PersistentQueue foundPersistentQueue = getPersistentQueue(cursorType);

            NodeEventCursorType nodeEventCursorType =
                    QueueCursorTypeMapper.get(foundPersistentQueue.getQueueConfiguration().getCursorType());
            if (nodeEventCursorType.isTimestampType()) {
                log.info("Resetting the queue by timestamp to {}", timeToReset);
                foundPersistentQueue.reset(foundPersistentQueue.getName(), timeToReset);
            } else {
                // Find the event id from the time
                log.debug("Resetting the queue by id. Searching for the first event id for the given timestamp {}",
                        timeToReset);
                long firstEventIdAfterProvidedTimestamp =
                        eventsService.getFirstEventIdAfterProvidedTimestamp(timeToReset, foundPersistentQueue.getName(),
                                foundPersistentQueue.getQueueConfiguration().getCursorType().name());
                if (firstEventIdAfterProvidedTimestamp == -1) {
                    log.debug("No events found after the provided timestamp. Updating queue: {} to status: {}",
                            foundPersistentQueue.getName(), status);
                    nodeEventCursorService.updateCursorWithStatus(foundPersistentQueue.getName(), status);
                    return;
                }
                log.info("Resetting to the given id: {}", firstEventIdAfterProvidedTimestamp);
                foundPersistentQueue.reset(foundPersistentQueue.getRepoKey(), firstEventIdAfterProvidedTimestamp);
            }
            log.debug("Updating to status: {}", status);
            foundPersistentQueue.updateStatus(status);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public void updateConstantQueueStatus(QueueCursorType cursorType, NodeEventCursorStatus status) {
        log.info("Updating Constant Queue status to: {}", status);
        PersistentQueue foundPersistentQueue = getPersistentQueue(cursorType);
        foundPersistentQueue.updateStatus(status);
    }

    private PersistentQueue getPersistentQueue(QueueCursorType cursorType) {
        return alwaysTriggerQueues.stream()
                .filter(persistentQueue -> persistentQueue.getQueueConfiguration().getCursorType()
                        .equals(cursorType)).findFirst()
                .orElseThrow(
                        () -> new BadRequestException("No queue found for cursor type %s".formatted(cursorType)));
    }

    @Override
    public void destroy() {
        readWriteLock.writeLock().lock();
        try {
            queuesPerRepo.values()
                    .stream()
                    .flatMap(queue -> queue.values().stream()).forEach(PersistentQueue::shutdown);
            queuesPerRepo.clear();
            alwaysTriggerQueues.clear();
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }
}