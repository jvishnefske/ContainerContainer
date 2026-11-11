package org.artifactory.aql.result.rows;

import com.google.common.collect.Sets;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.rows.populate.PhysicalFieldResultPopulator;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableFileInfo;
import org.artifactory.fs.MutableFolderInfo;
import org.artifactory.repo.RepoPath;

import jakarta.annotation.Nullable;
import java.util.Date;
import java.util.Objects;
import java.util.Set;

import static org.artifactory.checksum.ChecksumInfo.TRUSTED_FILE_MARKER;

public class ItemInfoRow implements RowResult {

    protected long itemId;
    protected String repo;
    protected String path;
    protected String name;
    protected long size;
    protected short depth;
    protected AqlItemTypeEnum typeEnum;

    protected long modified;
    protected long created;
    protected long updated;

    protected String createdBy;
    protected String modifiedBy;

    protected String originalMd5;
    protected String actualMd5;
    protected String originalSha1;
    protected String actualSha1;
    protected String sha2;

    protected String propKey;
    protected String propVal;

    @Override
    public void put(DomainSensitiveField field, Object value) {
        if (!(field.getField() instanceof AqlPhysicalFieldEnum)) {
            return;
        }
        put((AqlPhysicalFieldEnum) field.getField(), value);
    }

    public void put(AqlPhysicalFieldEnum field, Object value) {
        switch (field) {
            case itemId:
                itemId = (Long) value;
                break;
            case itemRepo:
                repo = (String) value;
                break;
            case itemPath:
                path = (String) value;
                break;
            case itemName:
                name = (String) value;
                break;
            case itemSize:
                size = (Long) value;
                break;
            case itemModified:
                modified = getTimeFromObject(value);
                break;
            case itemCreated:
                created = getTimeFromObject(value);
                break;
            case itemUpdated:
                updated = getTimeFromObject(value);
                break;
            case itemCreatedBy:
                createdBy = (String) value;
                break;
            case itemModifiedBy:
                modifiedBy = (String) value;
                break;
            case itemOriginalMd5:
                originalMd5 = (String) value;
                break;
            case itemActualMd5:
                actualMd5 = (String) value;
                break;
            case itemOriginalSha1:
                originalSha1 = (String) value;
                break;
            case itemActualSha1:
                actualSha1 = (String) value;
                break;
            case itemSha2:
                sha2 = (String) value;
                break;
            case propertyKey:
                propKey = (String) value;
                break;
            case propertyValue:
                propVal = (String) value;
                break;
            case itemDepth:
                depth = getDepthFromObject(value);
                break;
            case itemType:
                typeEnum = (AqlItemTypeEnum) value;
                break;
            default:
                //unsupported, ignore
        }
    }

    @Override
    public Object get(DomainSensitiveField field) {
        return null;
    }

    public RepoPath getRepoPath() {
        return AqlUtils.fromAql(repo, path, name, AqlItemTypeEnum.file == typeEnum);
    }

    public ItemInfo toItemInfo() {
        if (AqlItemTypeEnum.file == typeEnum) {
            MutableFileInfo fileInfo = InfoFactoryHolder.get().createFileInfo(getRepoPath(), itemId);
            fileInfo.setSize(size);
            fileInfo.setLastModified(modified);
            fileInfo.setCreated(created);
            fileInfo.setLastUpdated(updated);
            fileInfo.setCreatedBy(createdBy);
            fileInfo.setModifiedBy(modifiedBy);
            Set<ChecksumInfo> checksums = Sets.newHashSet();
            checksums.add(new ChecksumInfo(ChecksumType.md5, originalMd5, actualMd5));
            checksums.add(new ChecksumInfo(ChecksumType.sha1, originalSha1, actualSha1));
            if (StringUtils.isNotBlank(sha2)) {
                checksums.add(new ChecksumInfo(ChecksumType.sha256, TRUSTED_FILE_MARKER, sha2));
            }
            fileInfo.setChecksums(checksums);
            return fileInfo;
        } else {
            MutableFolderInfo folderInfo = InfoFactoryHolder.get().createFolderInfo(getRepoPath(), itemId);
            folderInfo.setLastModified(modified);
            folderInfo.setCreated(created);
            folderInfo.setLastUpdated(updated);
            folderInfo.setCreatedBy(createdBy);
            folderInfo.setModifiedBy(modifiedBy);
            return folderInfo;
        }
    }

    public String getPropKey() {
        return propKey;
    }

    public String getPropVal() {
        return propVal;
    }

    private short getDepthFromObject(@NonNull Object value) {
        if (value instanceof Integer) {
            return (short) (int) value;
        }
        return (short) value;
    }

    private long getTimeFromObject(Object value) {
        Date date;
        if (value instanceof Long) {
            date = (Date) PhysicalFieldResultPopulator.toDateObject.apply((long) value);
        } else {
            date = (Date) value;
        }
        return date == null ? 0 : date.getTime();
    }

    public boolean equalsToOther(@Nullable ItemInfoRow other) {
        return other != null &&
                this.itemId == other.itemId &&
                this.size == other.size &&
                this.depth == other.depth &&
                this.modified == other.modified &&
                this.created == other.created &&
                this.updated == other.updated &&
                Objects.equals(this.repo, other.repo) &&
                Objects.equals(this.path, other.path) &&
                Objects.equals(this.name, other.name) &&
                Objects.equals(this.typeEnum, other.typeEnum) &&
                Objects.equals(this.createdBy, other.createdBy) &&
                Objects.equals(this.modifiedBy, other.modifiedBy) &&
                Objects.equals(this.originalMd5, other.originalMd5) &&
                Objects.equals(this.actualMd5, other.actualMd5) &&
                Objects.equals(this.originalSha1, other.originalSha1) &&
                Objects.equals(this.actualSha1, other.actualSha1) &&
                Objects.equals(this.sha2, other.sha2) &&
                Objects.equals(this.propKey, other.propKey) &&
                Objects.equals(this.propVal, other.propVal);
    }
}