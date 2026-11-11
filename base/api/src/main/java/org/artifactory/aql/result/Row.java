package org.artifactory.aql.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlItemTypeEnum;

import org.artifactory.common.ConstantValues;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {"itemRepo", "itemPath", "itemName", "itemType", "itemSize", "itemCreated",
        "itemCreatedBy", "itemModified", "itemModifiedBy", "itemUpdated", "itemDepth"}, alphabetic = true)
public class Row {

    @JsonIgnore
    public Map<String, Row> subDomains;
    @JsonIgnore
    public AqlDomainEnum domain;
    @JsonIgnore
    public Long statId;
    @JsonIgnore
    public Long itemId;
    @JsonIgnore
    public Long propertyId;
    @JsonIgnore
    public Long propertyItemId;
    @JsonIgnore
    public Long buildId;
    @JsonIgnore
    public Long buildPropertyId;
    @JsonIgnore
    public Long buildArtifactId;
    @JsonIgnore
    public Long modulePropertyId;
    @JsonIgnore
    public Long moduleId;
    @JsonIgnore
    public Long buildDependencyId;
    @JsonIgnore
    public Long archiveEntryPathId;
    @JsonIgnore
    public Long statRemoteId;
    // release artifacts
    @JsonIgnore
    public Long releaseBundleFileId;
    @JsonIgnore
    public Long releaseBundleFileNodeId;
    @JsonIgnore
    public Long releaseBundleFileBundleId;
    @JsonIgnore
    public Long releaseBundleId;

    @JsonProperty("repo")
    public String itemRepo;
    @JsonProperty("path")
    public String itemPath;
    @JsonProperty("name")
    public String itemName;
    @JsonProperty("size")
    public Long itemSize;
    @JsonProperty("depth")
    public Integer itemDepth;
    @JsonProperty("modified")
    public String itemModified;
    @JsonProperty("created")
    public String itemCreated;
    @JsonProperty("updated")
    public String itemUpdated;
    @JsonProperty("created_by")
    public String itemCreatedBy;
    @JsonProperty("modified_by")
    public String itemModifiedBy;
    @JsonProperty("type")
    public AqlItemTypeEnum itemType;
    @JsonProperty("virtual_repos")
    public String[] itemVirtualRepos;
    @JsonProperty("original_md5")
    public String itemOriginalMd5;
    @JsonProperty("actual_md5")
    public String itemActualMd5;
    @JsonProperty("original_sha1")
    public String itemOriginalSha1;
    @JsonProperty("actual_sha1")
    public String itemActualSha1;
    @JsonProperty("sha256")
    public String itemSha2;
    @JsonProperty("repo_path_checksum")
    public String repoPathChecksum;
    @JsonProperty("downloaded")
    public String statDownloaded;
    @JsonProperty("downloads")
    public Integer statDownloads;
    @JsonProperty("downloaded_by")
    public String statDownloadedBy;
    @JsonProperty("remote_downloaded_by")
    public String statRemoteDownloadedBy;
    @JsonProperty("remote_downloads")
    public Integer statRemoteDownloads;
    @JsonProperty("remote_downloaded")
    public String statRemoteDownloaded;
    @JsonProperty("remote_path")
    public String statRemotePath;
    @JsonProperty("remote_origin")
    public String statRemoteOrigin;
    @JsonProperty("key")
    public String propertyKey;
    @JsonProperty("value")
    public String propertyValue;
    @JsonProperty("item.props_modified")
    public String itemInfosPropsModified;
    @JsonProperty("item.props_modofied_by")
    public String itemInfosPropsModifiedBy;
    @JsonProperty("item.props_md5")
    public String itemInfosPropsMd5;
    @JsonProperty("entry.name")
    public String archiveEntryName;
    @JsonProperty("entry.path")
    public String archiveEntryPath;
    @JsonProperty("module.name")
    public String moduleName;
    @JsonProperty("module.property.key")
    public String modulePropertyKey;
    @JsonProperty("module.property.value")
    public String modulePropertyValue;
    @JsonProperty("dependency.name")
    public String buildDependencyName;
    @JsonProperty("dependency.scope")
    public String buildDependencyScope;
    @JsonProperty("dependency.type")
    public String buildDependencyType;
    @JsonProperty("dependency.sha1")
    public String buildDependencySha1;
    @JsonProperty("dependency.md5")
    public String buildDependencyMd5;
    @JsonProperty("artifact.name")
    public String buildArtifactName;
    @JsonProperty("artifact.type")
    public String buildArtifactType;
    @JsonProperty("artifact.sha1")
    public String buildArtifactSha1;
    @JsonProperty("artifact.md5")
    public String buildArtifactMd5;
    @JsonProperty("build.property.key")
    public String buildPropertyKey;
    @JsonProperty("build.property.value")
    public String buildPropertyValue;
    @JsonProperty("build.url")
    public String buildUrl;
    @JsonProperty("build.name")
    public String buildName;
    @JsonProperty("build.number")
    public String buildNumber;
    @JsonProperty("build.started")
    public String buildStarted;
    @JsonProperty("build.repo")
    public String buildRepo;
    @JsonProperty("build.created")
    public String buildCreated;
    @JsonProperty("build.created_by")
    public String buildCreatedBy;
    @JsonProperty("build.modified")
    public String buildModified;
    @JsonProperty("build.modified_by")
    public String buildModifiedBy;
    @JsonProperty("build.promotion.created")
    public String buildPromotionCreated;
    @JsonProperty("build.promotion.created_by")
    public String buildPromotionCreatedBy;
    @JsonProperty("build.promotion.user")
    public String buildPromotionUserName;
    @JsonProperty("build.promotion.status")
    public String buildPromotionStatus;
    @JsonProperty("build.promotion.repo")
    public String buildPromotionRepo;
    @JsonProperty("build.promotion.comment")
    public String buildPromotionComment;
    // Release bundle
    @JsonProperty("release.name")
    public String releaseBundleName;
    @JsonProperty("release.version")
    public String releaseBundleVersion;
    @JsonProperty("release.status")
    public String releaseBundleStatus;
    @JsonProperty("release.created")
    public String releaseBundleCreated;
    @JsonProperty("release.signature")
    public String releaseBundleSignature;
    @JsonProperty("release.type")
    public String releaseBundleType;
    @JsonProperty("release.storing_repo")
    public String releaseBundleStoringRepo;
    @JsonProperty("release.keep")
    public Integer releaseBundleKeep;
    @JsonProperty("release.source_service_id")
    public String releaseBundleSourceServiceId;

    // Release bundle files
    @JsonProperty("release_artifact.repo_path")
    public String releaseBundleFileRepoPath;

    @JsonProperty("items")
    public List<Row> items;
    @JsonProperty("properties")
    public List<Row> properties;
    @JsonProperty("item.infos")
    public List<Row> itemInfos;
    @JsonProperty("stats")
    public List<Row> statistics;
    @JsonProperty("artifacts")
    public List<Row> artifacts;
    @JsonProperty("dependencies")
    public List<Row> dependencies;
    @JsonProperty("modules")
    public List<Row> modules;
    @JsonProperty("module.properties")
    public List<Row> moduleProperties;
    @JsonProperty("builds")
    public List<Row> builds;
    @JsonProperty("build.properties")
    public List<Row> buildProperties;
    @JsonProperty("build.promotions")
    public List<Row> buildPromotions;
    @JsonProperty("releaseBundles")
    public List<Row> releaseBundles;
    @JsonProperty("releaseBundleFiles")
    public List<Row> releaseBundleFiles;

    public Row() {
        super();
    }

    public Row(AqlDomainEnum domain) {
        this.domain = domain;
    }

    private static final Field[] FIELDS_EXCLUDING_DOMAINS;
    private static final Map<String, Field> FIELDS_MAP_BY_NAME = new HashMap<>();

    static {
        Field[] allPublic = Row.class.getFields();
        List<Field> forMap = new ArrayList<>();
        for (Field f : Row.class.getDeclaredFields()) {
            FIELDS_MAP_BY_NAME.put(f.getName(), f);
        }
        for (Field f : allPublic) {
            if (!"subDomains".equals(f.getName()) && !"domain".equals(f.getName())) {
                forMap.add(f);
            }
        }
        FIELDS_EXCLUDING_DOMAINS = forMap.toArray(Field[]::new);
    }

    public static Field[] getCleanCheckFields() {
        return FIELDS_EXCLUDING_DOMAINS;
    }

    public void put(String fieldName, Object value) {
        if (ConstantValues.inflateRowEnhancementEnabled.getBoolean()) {
            try {
                Field field = FIELDS_MAP_BY_NAME.get(fieldName);
                if (field != null) {
                    field.set(this, value);
                }
            } catch (Exception e) {
                log.error("Failed to fill Aql result {}: with value: {}", fieldName, value, e);
            }
        } else {
            try {
                Field declaredField = getClass().getDeclaredField(fieldName);
                declaredField.set(this, value);
            } catch (Exception e) {
                log.error("Failed to fill Aql result {}: with value: {}", fieldName, value, e);
            }
        }
    }

    public void merge(Row row) {
        merge(row, this);
    }

    private boolean merge(Row source, Row target) {
        boolean containsData = mapFields(source, target);
        boolean childContainsData = false;
        if (source.subDomains != null) {
            for (String id : source.subDomains.keySet()) {
                Row sourceSubRow = source.subDomains.get(id);
                if (target.subDomains == null) {
                    target.subDomains = Maps.newHashMap();
                }
                Row targetSubRow = target.subDomains.get(id);
                if (targetSubRow == null) {
                    targetSubRow = new Row(sourceSubRow.getDomain());
                    target.subDomains.put(id, targetSubRow);
                }
                childContainsData = merge(sourceSubRow, targetSubRow);
                if (!childContainsData) {
                    target.subDomains.remove(id);
                }
            }
        }
        return containsData || childContainsData;
    }

    private boolean mapFields(Row source, Row target) {
        try {
            if (ConstantValues.inflateRowEnhancementEnabled.getBoolean()) {
                for (Field field : FIELDS_EXCLUDING_DOMAINS) {
                    Object value = field.get(source);
                    field.set(target, value);
                }
                return true;
            }
            Field[] declaredFields = source.getClass().getFields();
            boolean containsData = false;
            for (Field declaredField : declaredFields) {
                if (!declaredField.getName().equals("subDomains") && !declaredField.getName().equals("domain")) {
                    Object value = declaredField.get(source);
                    //Special behaviour for archive domain, since archive domain is built by two tables and
                    //doesn't have real key
                    containsData = true;
                    declaredField.set(target, value);
                }
            }
            return containsData;
        } catch (IllegalAccessException e) {
            throw new AqlException("failed to map result fields", e);
        }
    }

    public Row build() {
        if (subDomains != null) {
            for (Row row : subDomains.values()) {
                row.build();
                AqlDomainEnum domainEnum = row.getDomain();
                switch (domainEnum) {
                    case items -> {
                        if (this.items == null) {
                            this.items = Lists.newArrayList();
                        }
                        this.items.add(row);
                    }
                    case properties -> {
                        if (this.properties == null) {
                            this.properties = Lists.newArrayList();
                        }
                        this.properties.add(row);
                    }
                    case itemInfos -> {
                        if (this.itemInfos == null) {
                            this.itemInfos = Lists.newArrayList();
                        }
                        this.itemInfos.add(row);
                    }
                    case statistics -> {
                        if (this.statistics == null) {
                            this.statistics = Lists.newArrayList();
                        }
                        this.statistics.add(row);
                    }
                    case artifacts -> {
                        if (this.artifacts == null) {
                            this.artifacts = Lists.newArrayList();
                        }
                        this.artifacts.add(row);
                    }
                    case dependencies -> {
                        if (this.dependencies == null) {
                            this.dependencies = Lists.newArrayList();
                        }
                        this.dependencies.add(row);
                    }
                    case modules -> {
                        if (this.modules == null) {
                            this.modules = Lists.newArrayList();
                        }
                        this.modules.add(row);
                    }
                    case moduleProperties -> {
                        if (this.moduleProperties == null) {
                            this.moduleProperties = Lists.newArrayList();
                        }
                        this.moduleProperties.add(row);
                    }
                    case builds -> {
                        if (this.builds == null) {
                            this.builds = Lists.newArrayList();
                        }
                        this.builds.add(row);
                    }
                    case buildProperties -> {
                        if (this.buildProperties == null) {
                            this.buildProperties = Lists.newArrayList();
                        }
                        this.buildProperties.add(row);
                    }
                    case buildPromotions -> {
                        if (this.buildPromotions == null) {
                            this.buildPromotions = Lists.newArrayList();
                        }
                        this.buildPromotions.add(row);
                    }
                    case releaseBundles -> {
                        if (this.releaseBundles == null) {
                            this.releaseBundles = Lists.newArrayList();
                        }
                        this.releaseBundles.add(row);
                    }
                    case releaseBundleFiles -> {
                        if (this.releaseBundleFiles == null) {
                            this.releaseBundleFiles = Lists.newArrayList();
                        }
                        this.releaseBundleFiles.add(row);
                    }
                }
            }
        }
        return this;
    }

    public AqlDomainEnum getDomain() {
        return domain;
    }

    public String getItemPath() {
        return itemPath;
    }

    public String getItemName() {
        return itemName;
    }

    public Integer getItemDepth() {
        return itemDepth;
    }
}
