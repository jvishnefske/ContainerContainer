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

package org.artifactory.storage.db.fs.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import lombok.NonNull;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.exception.CancelException;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableItemInfo;
import org.artifactory.fs.WatcherInfo;
import org.artifactory.md.Properties;
import org.artifactory.md.PropertiesInfo;
import org.artifactory.model.fs.ItemInfoUtils;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.security.AccessLogger;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.event.service.EventsSessionResource;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.VfsItemEvent;
import org.artifactory.storage.fs.repo.StoringRepo;
import org.artifactory.storage.fs.service.ConditionalInsertContext;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.artifactory.storage.spring.StorageContextHelper;
import org.artifactory.storage.tx.SessionEventHandler;
import org.artifactory.util.PathValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

/**
 * Base class for the mutable folder and file.
 *
 * @author Yossi Shaul
 */
public abstract class DbMutableItem<T extends MutableItemInfo> extends DbFsItem<T> implements MutableVfsItem<T> {
    private static final Logger log = LoggerFactory.getLogger(DbMutableItem.class);

    /**
     * The mutable item info. All the item modifications done during this session are saved in this object.
     */
    final T mutableInfo;
    /**
     * True if this item is marked for deletion on session save
     */
    boolean markForDeletion;

    /**
     * True if this item is marked in error state and should not be saved in the session.
     *
     * @see DbMutableItem#markError()
     */
    boolean inError;
    /**
     * Original item info captures the state of the item before any changes are done to the mutable item.
     * This is used to check if there are any pending changes to save on session commit. The original info is reset
     * with the mutable info after the item is saved.
     */
    private ItemInfo originalItemInfo;
    /**
     * Virgin item info captures the original state and does not change all along the session this mutable item
     * participates in.
     */
    private ItemInfo virginItemInfo;
    private Properties properties;
    /**
     * Virgin properties capture the original state and do not change all along the session this mutable item
     * participates in.
     */
    private PropertiesInfo virginProperties;
    private List<WatcherInfo> watchesToAdd;
    /**
     * True if this item was deleted from the db (after a session save)
     */
    private boolean deleted;

    @VisibleForTesting
    LongSupplier currentTimeSupplier = System::currentTimeMillis;

    DbMutableItem(StoringRepo repo, long id, T info) {
        super(repo, id, info);
        PathValidator.validate(info.getRepoPath().toPath());
        this.id = id;
        this.mutableInfo = info;
        originalItemInfo = InfoFactoryHolder.get().copyItemInfo(info);
        virginItemInfo = InfoFactoryHolder.get().copyItemInfo(info);
    }

    DbMutableItem(StoringRepo repo, long id, T info, ItemInfo originalInfo,
            Properties originalProperties, Properties newProperties) {
        super(repo, id, info);
        PathValidator.validate(info.getRepoPath().toPath());
        this.id = id;
        this.mutableInfo = info;
        virginItemInfo = InfoFactoryHolder.get().copyItemInfo(originalInfo);
        originalItemInfo = InfoFactoryHolder.get().copyItemInfo(originalInfo);
        virginProperties = originalProperties;
        properties = newProperties;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    @Nonnull
    public Properties getProperties() {
        if (properties != null) {
            return properties;
        } else {
            return super.getProperties();
        }
    }

    @Override
    public void setProperties(Properties properties) {
        notifyChanges();
        //Use change tracking ability of the properties
        Properties props = this.getProperties();
        if (props == properties) {
            return;
        }
        props.clear();
        props.putAll(properties);
        this.properties = props;
    }

    @Override
    public boolean markOriginalProperties() {
        if (virginProperties == null) {
            virginProperties = new PropertiesImpl(getProperties());
            return true;
        }
        return false;
    }

    @Override
    @Nonnull
    public PropertiesInfo getOriginalProperties() {
        if (!isOriginalPropertiesAvailable()) {
            throw new IllegalStateException("Original properties are not available.");
        }
        return virginProperties;
    }

    @Override
    public boolean isOriginalPropertiesAvailable() {
        return virginProperties != null;
    }

    @Override
    public ItemInfo getOriginalInfo() {
        return virginItemInfo;
    }

    @Override
    public void addWatch(WatcherInfo watch) {
        if (watchesToAdd == null) {
            watchesToAdd = Lists.newArrayList();
        }
        watchesToAdd.add(watch);
    }

    @Override
    public void markError() {
        inError = true;
    }

    @Override
    public void releaseResources() {
        // nothing to release by default
    }

    @Override
    public boolean isFile() {
        return !info.isFolder();
    }

    @Override
    public boolean isFolder() {
        return info.isFolder();
    }

    @Override
    public boolean isNew() {
        return id == DbService.NO_DB_ID;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public boolean hasPendingChanges() {
        if (inError) {
            return false;
        }
        return isNew() || (markForDeletion && !deleted) || properties != null ||
                watchesToAdd != null || !mutableInfo.isIdentical(originalItemInfo);
    }

    @Override
    public boolean isMarkedForDeletion() {
        return markForDeletion;
    }

    @Override
    public boolean save() {
        if (inError) {
            throw new IllegalStateException("Attempt to save an item in error state: " + this);
        }
        if (isNew()) {
            if (!readyForPersistence()) {
                // session save might be called before an item is ready to be persisted in the database
                // in such case delay the save and expect a later session save to persist
                // if no other session save is called, an error will be emitted to the log on session remove
                log.debug("Item '{}' is not ready for persistence", getRepoPath());
                return false;
            }
            saveNew();
            if (ConditionalInsertContext.wasSkipped()) {
                ConditionalInsertContext.setSkipped(false);
                return true;
            }
        } else if (markForDeletion) {
            log.debug("Deleting item: '{}'", getRepoPath());
            invokeDeleteOperations(id);
        } else {    // it's an update
            saveUpdate();
        }

        // reset original info
        resetAfterSave();
        return true;
    }

    /**
     * This method handles new item and Save the item properly
     */
    private void saveNew() {
        id = doCreateNode();
        if (ConditionalInsertContext.wasSkipped()) {
            return;
        }
        fireAfterCreateEvent();

        if (properties != null && !properties.isEmpty()) { // no point to set empty props for new item
            getPropertiesService().setProperties(getRepoPath(), properties);
            fireAfterPropertiesModifiedEvent();
        }
        if (watchesToAdd != null) {
            addWatches();
        }
    }

    /**
     * This method handles existing item and Save the updated item properly
     */
    private void saveUpdate() {
        if (!mutableInfo.isIdentical(originalItemInfo)) {
            doUpdateNode();
            fireAfterUpdateEvent();
        }
        if (properties != null) {
            boolean modified = getPropertiesService().setProperties(getRepoPath(), properties);
            if (modified) {
                fireAfterPropertiesModifiedEvent();
            }
        }
        if (watchesToAdd != null) {
            addWatches();
        }
    }

    private void invokeDeleteOperations(long id) {
        DbMutableItemDeleteHelper deleteHelper = ContextHelper.get().beanForType(DbMutableItemDeleteHelper.class);
        deleteHelper.deleteItem(id);
        deleted = true;
    }

    @Override
    public void setCreated(long created) {
        notifyChanges();
        mutableInfo.setCreated(created);
    }

    @Override
    public void setCreatedBy(String user) {
        notifyChanges();
        mutableInfo.setCreatedBy(user);
    }

    @Override
    public void setModified(long modified) {
        notifyChanges();
        mutableInfo.setLastModified(modified);
    }

    @Override
    public void setModifiedBy(String user) {
        notifyChanges();
        mutableInfo.setModifiedBy(user);
    }

    @Override
    public void setUpdated(long updated) {
        notifyChanges();
        mutableInfo.setLastUpdated(updated);
    }

    /**
     * Call a given function with the virgin and current immutable item infos
     *
     * @param function the function to apply
     * @param <R>      the return type of the function
     * @return the return value of the function
     */
    <R> R doWithOriginalAndCurrentItemInfos(BiFunction<ItemInfo, ItemInfo, R> function) {
        return function.apply(virginItemInfo, mutableInfo);
    }

    private void addWatches() {
        getWatchesService().addWatches(id, watchesToAdd);
    }

    protected void resetAfterSave() {
        markForDeletion = false;
        watchesToAdd = null;
        properties = null;
        originalItemInfo = InfoFactoryHolder.get().copyItemInfo(mutableInfo);
    }

    protected abstract long doCreateNode();

    protected abstract void doUpdateNode();

    protected abstract boolean readyForPersistence();

    void fillInfo(ItemInfo source) {
        if (ItemInfoUtils.isUpdateCreatedTimestampOnCopyOrMoveEnabled()) {
            setCreated(currentTimeSupplier.getAsLong());
        } else {
            setCreated(source.getCreated());
        }
        setCreatedBy(source.getCreatedBy());
        setModified(source.getLastModified());
        setModifiedBy(source.getModifiedBy());
        setUpdated(source.getLastUpdated());
    }

    boolean canDelete() throws CancelException {
        return canDelete(new DeleteContext(getRepoPath()));
    }

    boolean canDelete(DeleteContext ctx) throws CancelException {
        StorageInterceptors interceptors = StorageContextHelper.get().beanForType(StorageInterceptors.class);
        BasicStatusHolder statusHolder = getStatusHolder();
        boolean canDelete = interceptors.canDelete(getRepoPath(), statusHolder, ctx);
        if (statusHolder.isError()) {
            throw statusHolder.getCancelException();
        }
        return canDelete;
    }

    void fireBeforeDeleteEvent(DeleteContext ctx) throws CancelException {
        StorageInterceptors interceptors = StorageContextHelper.get().beanForType(StorageInterceptors.class);
        BasicStatusHolder statusHolder = getStatusHolder();
        interceptors.beforeDelete(this, statusHolder, ctx);
        if (statusHolder.isError()) {
            throw statusHolder.getCancelException();
        }
    }

    void fireAfterDeleteEvent(DeleteContext ctx, BasicStatusHolder statusHolder) {
        StorageInterceptors interceptors = StorageContextHelper.get().beanForType(StorageInterceptors.class);
        interceptors.afterDelete(this, statusHolder, ctx);
        AccessLogger.deleted(getRepoPath());
        //TODO: [by YS] this event doesn't fire exactly where I prefer it. but will try to use this same entry point
        getEventsSessionResource().itemDeleted(this);
        if (ctx.isMarkForEventsGc()) {
            ApplicationEventPublisher eventPublisher = ContextHelper.get().getEventPublisher();
            eventPublisher.publishEvent(new VfsItemEvent(this, VfsItemEvent.Type.delete));
        }
    }

    private void fireAfterCreateEvent() {
        getEventsSessionResource().itemCreated(this);
    }

    protected void fireAfterUpdateEvent() {
        getEventsSessionResource().itemUpdated(this);
    }

    private void fireAfterPropertiesModifiedEvent() {
        getEventsSessionResource().propertiesModified(this);
    }

    private SessionEventHandler getEventsSessionResource() {
        return StorageSessionHolder.getSession().getOrCreateResource(EventsSessionResource.class);
    }

    void notifyChanges() {
        if (!hasPendingChanges()) { // notify only before the first change to the item
            StorageSessionHolder.getSession().addPendingOperation(mutableInfo.getRepoPath());
        }
    }

    @VisibleForTesting
    @NonNull
    static BasicStatusHolder getStatusHolder() {
        return new BasicStatusHolder();
    }
}
