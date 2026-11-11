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

import com.google.common.collect.Maps;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.binary.headers.BinaryHeaders;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.exception.CancelException;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableFileInfo;
import org.artifactory.fs.StatsInfo;
import org.artifactory.md.Properties;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.sh.acl.BinaryHeadersUtil;
import org.artifactory.storage.BinaryInsertRetryException;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.storage.fs.Sha256ConvertableMutableVfsFile;
import org.artifactory.storage.fs.VfsException;
import org.artifactory.storage.fs.VfsItemEvent;
import org.artifactory.storage.fs.repo.StoringRepo;
import org.artifactory.storage.fs.service.ConditionalInsertContext;
import org.artifactory.storage.fs.service.StatsService;
import org.jfrog.storage.binstore.common.BinaryElementImpl;
import org.jfrog.storage.binstore.ifc.BinaryServiceInputStream;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.valueOf;
import static org.artifactory.checksum.ChecksumInfo.TRUSTED_FILE_MARKER;
import static org.artifactory.common.ConstantValues.sha256ClientChecksumFix;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.CONTENT_LENGTH;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.REPOSITORY_KEY;

/**
 * A mutable DB file.
 *
 * @author Yossi Shaul
 */
public class DbMutableFile extends DbMutableItem<MutableFileInfo>
        implements MutableVfsFile, Sha256ConvertableMutableVfsFile {
    private static final Logger log = LoggerFactory.getLogger(DbMutableFile.class);

    private StatsInfo stats;
    private AtomicBoolean forceUpdateStats = new AtomicBoolean(false);

    public DbMutableFile(StoringRepo storingRepo, long id, FileInfo info) {
        super(storingRepo, id, InfoFactoryHolder.get().copyFileInfo(info));
    }

    public DbMutableFile(StoringRepo storingRepo, long id, FileInfo info, ItemInfo originalItemInfo,
            Properties originalProperties, Properties newProperties) {
        super(storingRepo, id, InfoFactoryHolder.get().copyFileInfo(info), originalItemInfo, originalProperties,
                newProperties);
    }

    @Override
    public void setClientChecksum(@Nonnull ChecksumType type, @Nullable String checksum) {
        switch (type) {
            case sha1:
                setClientSha1(checksum);
                break;
            case md5:
                setClientMd5(checksum);
                break;
            case sha256:
                if (sha256ClientChecksumFix.getBoolean()) {
                    setClientSha2(checksum);
                }
                break;
        }
    }

    @Override
    public void setClientSha1(@Nullable String sha1) {
        notifyChanges();
        mutableInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha1, sha1, mutableInfo.getSha1()));
    }

    @Override
    public void setClientSha2(@Nullable String sha2) {
        notifyChanges();
        mutableInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256, sha2, mutableInfo.getSha2()));
    }

    @Override
    public void setClientMd5(String md5) {
        notifyChanges();
        mutableInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.md5, md5, mutableInfo.getMd5()));
    }

    @Override
    public void fillInfo(FileInfo source) {
        super.fillInfo(source);
        ChecksumsInfo resourceChecksums = source.getChecksumsInfo();
        ChecksumInfo sha1Info = resourceChecksums.getChecksumInfo(ChecksumType.sha1);
        String clientSha1 = sha1Info == null ? null : sha1Info.getOriginalOrNoOrig();
        setClientSha1(clientSha1);
        ChecksumInfo sha2Info = resourceChecksums.getChecksumInfo(ChecksumType.sha256);
        String clientSha2 = sha2Info == null ? null : sha2Info.getOriginalOrNoOrig();
        setClientSha2(clientSha2);
        ChecksumInfo md5Info = resourceChecksums.getChecksumInfo(ChecksumType.md5);
        String clientMd5 = md5Info == null ? null : md5Info.getOriginalOrNoOrig();
        setClientMd5(clientMd5);
    }

    @Override
    public boolean tryUsingExistingBinary(String sha1, String sha2, String md5, long length)
            throws BinaryInsertRetryException {
        Map<String, String> headers = Maps.newHashMap();
        headers.put(REPOSITORY_KEY.getHeaderName(), getRepoKey());
        BinaryElement binary = getBinaryStore().addBinaryRecord(headers, sha1, sha2, md5, length);
        if (binary != null) {
            setFileBinaryInfo(binary);
            return true;
        }
        return false;
    }

    @Override
    public boolean isChecksumChanged() {
        return doWithOriginalAndCurrentItemInfos((original, current) -> {
            FileInfo origFileInfo = (FileInfo) original;
            FileInfo currFileInfo = (FileInfo) current;
            String origSha1 = origFileInfo.getChecksumsInfo().getSha1();
            String currSha1 = currFileInfo.getChecksumsInfo().getSha1();
            return !StringUtils.equals(origSha1, currSha1);
        });
    }

    @Override
    public boolean isOriginallyNew() {
        // isNew is based on the existence of the node id. A new item is saved before afterCreate interceptors
        // are called. This causes new items to return isNew == false in the interceptor. This method returns whether
        // the item was originally new in the current transaction, based on whether it originally had a checksum.
        return doWithOriginalAndCurrentItemInfos((original, current) -> {
            FileInfo origFileInfo = (FileInfo) original;
            return origFileInfo.getChecksumsInfo().getSha1() == null;
        });
    }

    @Override
    public void fillBinaryData(InputStream in) {
        //Check if needs to create checksum and not checksum file
        log.debug("Calculating checksums of '{}'.", getRepoPath());
        BinaryElement binaryInfo;
        try {
            if (in instanceof BinaryServiceInputStream) {
                // input stream is from existing binary
                binaryInfo = ((BinaryServiceInputStream) in).getBinaryInfo();
            } else {
                // call the binary service to add the current binary.
                BinaryHeaders binaryHeaders = new BinaryHeaders(getExpectedChecksums(), valueOf(getInfo().getSize()),
                        getRepoPath().getRepoKey());
                binaryInfo = getBinaryStore().addBinary(in, BinaryHeadersUtil.getExpectedHeaders(binaryHeaders));
            }
        } catch (IOException e) {
            throw new VfsException("Failed to add input stream for '" + getRepoPath() + "'", e);
        }
        setFileBinaryInfo(binaryInfo);
    }

    //use already-populated client checksums as expected values if the repo's checksum policy requires it.
    private ChecksumsInfo getExpectedChecksums() {
        return getRepo().getChecksumPolicy().shouldVerifyBadClientChecksum() ? mutableInfo.getChecksumsInfo() : null;
    }

    @Override
    public void setStats(StatsInfo statsInfo) {
        notifyChanges();
        this.stats = statsInfo;
    }

    @Override
    public void setFileBinaryInfo(String sha1, String sha2, String md5, long length) {
        setFileBinaryInfo(new BinaryElementImpl(sha1, sha2, md5, length));
    }

    public void setFileBinaryInfo(@Nonnull BinaryElement binary) {
        notifyChanges();
        // The size needs to be always consistent so no rely on MD
        mutableInfo.setSize(binary.getLength());
        ChecksumInfo sha1Orig = mutableInfo.getChecksumsInfo().getChecksumInfo(ChecksumType.sha1);
        String clientSha1 = sha1Orig == null ? null : sha1Orig.getOriginalOrNoOrig();
        mutableInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha1, clientSha1, binary.getSha1()));

        // sha256 original (client-provided) is used in-memory to validate against the actual value during upload,
        // but is intentionally NOT written to the database — there is no sha256_original column in the nodes table.
        // sha256 is always reloaded from DB as TRUSTED_FILE_MARKER (always trusted). See FileServiceImpl for context.
        ChecksumInfo sha2Orig = mutableInfo.getChecksumsInfo().getChecksumInfo(ChecksumType.sha256);
        String clientSha2 = sha2Orig == null ? null : sha2Orig.getOriginalOrNoOrig();
        mutableInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256, clientSha2, binary.getSha2()));
        ChecksumInfo md5Orig = mutableInfo.getChecksumsInfo().getChecksumInfo(ChecksumType.md5);
        String clientMd5 = md5Orig == null ? null : md5Orig.getOriginalOrNoOrig();
        mutableInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.md5, clientMd5, binary.getMd5()));
    }

    @Override
    public void delete(DeleteContext ctx) {
        delete(ctx, new BasicStatusHolder());
    }

    @Override
    public void delete(DeleteContext ctx, BasicStatusHolder statusHolder) {
        if (isNew()) {
            log.error("Deleting a newly created item: {}", getRepoPath());
            throw new IllegalStateException("Cannot delete a newly created item: " + getRepoPath());
        }
        if (markForDeletion) {
            return;
        }

        try {
            if (!canDelete(ctx)) {
                log.debug("Deletion of file {} is not allowed, skipping it...", getRepoPath());
                return;
            }
            fireBeforeDeleteEvent(ctx);
        } catch (CancelException e) {
            log.info("Deletion of {} was canceled by plugin", getRepoPath());
            throw e;
        }

        notifyChanges();
        markForDeletion = true;

        fireAfterDeleteEvent(ctx, statusHolder);
    }


    @Override
    public void releaseResources() {
        super.releaseResources();
    }

    @Override
    public boolean hasPendingChanges() {
        if (inError) {
            return false;
        }
        return stats != null || super.hasPendingChanges();
    }

    @Override
    protected boolean readyForPersistence() {
        return !StringUtils.isBlank(getSha1());
    }

    @Override
    protected void resetAfterSave() {
        super.resetAfterSave();
        stats = null;
    }

    @Override
    public void revertChanges() {
        if (isNew()) {
            inError = true;
        } else {
            resetAfterSave();
        }
    }

    @Override
    protected long doCreateNode() {
        long nodeId = getFileService().createFile(mutableInfo);
        if (stats != null && !ConditionalInsertContext.wasSkipped()) {
            getStatsService().setStats(nodeId, stats);
        }
        return nodeId;
    }

    @Override
    protected void doUpdateNode() {
        getFileService().updateFile(id, mutableInfo);
        // stats are updatable by mutable file only for import (force update)
        if (forceUpdateStats.compareAndSet(true, false) && stats != null) {
            getStatsService().setStats(id, stats);
        }
    }

    @Override
    public String getSha1() {
        return mutableInfo.getSha1();
    }

    @Override
    public String getSha2() {
        return mutableInfo.getSha2();
    }

    @Override
    public String getMd5() {
        return mutableInfo.getMd5();
    }

    @Override
    public InputStream getStream() {
        //[sha2]: switch to sha2 when binarystore switches to sha2
        Map<String, String> headers = Maps.newHashMap();
        headers.put(REPOSITORY_KEY.getHeaderName(), getRepoKey());
        headers.put(CONTENT_LENGTH.getHeaderName(), String.valueOf(length()));
        return getBinariesService().getBinary(getSha1(), headers);
    }

    @Override
    public long length() {
        return mutableInfo.getSize();
    }

    private ArtifactoryBinaryService getBinaryStore() {
        return ContextHelper.get().beanForType(ArtifactoryBinaryService.class);
    }

    protected StatsService getStatsService() {
        return ContextHelper.get().beanForType("statsServiceImpl", StatsService.class);
    }

    //DONT USE ME!! Only for sha256 conversion logic!
    @Override
    public void setSha2(String sha2) {
        mutableInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256, TRUSTED_FILE_MARKER, sha2));
    }

    @Override
    protected void fireAfterUpdateEvent() {
        super.fireAfterUpdateEvent();
        if (getOriginalInfo() instanceof FileInfo originalFileInfo &&
                !mutableInfo.getSha1().equals(originalFileInfo.getSha1())) {
            ApplicationEventPublisher eventPublisher = ContextHelper.get().getEventPublisher();
            eventPublisher.publishEvent(new VfsItemEvent(this, VfsItemEvent.Type.update));
        }
    }

    @Override
    public void forceUpdateStats() {
        forceUpdateStats.set(true);
    }
}
