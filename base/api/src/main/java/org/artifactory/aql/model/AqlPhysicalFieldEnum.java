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

package org.artifactory.aql.model;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.aql.model.AqlDomainEnum.artifacts;
import static org.artifactory.aql.model.AqlDomainEnum.buildPromotions;
import static org.artifactory.aql.model.AqlDomainEnum.buildProperties;
import static org.artifactory.aql.model.AqlDomainEnum.builds;
import static org.artifactory.aql.model.AqlDomainEnum.dependencies;
import static org.artifactory.aql.model.AqlDomainEnum.itemInfos;
import static org.artifactory.aql.model.AqlDomainEnum.items;
import static org.artifactory.aql.model.AqlDomainEnum.moduleProperties;
import static org.artifactory.aql.model.AqlDomainEnum.modules;
import static org.artifactory.aql.model.AqlDomainEnum.properties;
import static org.artifactory.aql.model.AqlDomainEnum.releaseBundleFiles;
import static org.artifactory.aql.model.AqlDomainEnum.releaseBundles;
import static org.artifactory.aql.model.AqlDomainEnum.statistics;
import static org.artifactory.aql.model.AqlVariableTypeEnum.date;
import static org.artifactory.aql.model.AqlVariableTypeEnum.integer;
import static org.artifactory.aql.model.AqlVariableTypeEnum.longInt;
import static org.artifactory.aql.model.AqlVariableTypeEnum.string;

/**
 * @author Gidi Shabat
 *
 * This class contains all the physical fields (domain, native name and type) supported by AQL
 * In order to add new Field to AQL, just add new field to this class and update acordinatlly the
 * AqlFieldExtensionEnum class
 */
public enum AqlPhysicalFieldEnum implements AqlFieldEnum {
    // node
    itemRepo("repo", items, string, true),
    itemPath("path", items, string, true),
    itemName("name", items, string, true),
    itemCreated("created", items, date, true),
    itemModified("modified", items, date, true),
    itemUpdated("updated", items, date, true),
    itemCreatedBy("created_by", items, string, true),
    itemModifiedBy("modified_by", items, string, true),
    itemType("type", items, AqlVariableTypeEnum.itemType, true),
    itemDepth("depth", items, integer, false),
    itemId("id", items, longInt, false),
    itemOriginalMd5("original_md5", items, string, false),
    itemActualMd5("actual_md5", items, string, false),
    itemOriginalSha1("original_sha1", items, string, false),
    itemActualSha1("actual_sha1", items, string, false),
    itemSha2("sha256", items, string, false),
    repoPathChecksum("repo_path_checksum", items, string, false),
    itemSize("size", items, longInt, true),
    // stats
    statDownloaded("downloaded", statistics, date, true),
    statDownloads("downloads", statistics, integer, true),
    statDownloadedBy("downloaded_by", statistics, string, true),
    statId("id", statistics, longInt, false),
    // remote stats
    statRemoteDownloaded("remote_downloaded", statistics, date, false),
    statRemoteDownloads("remote_downloads", statistics, integer, false),
    statRemoteDownloadedBy("remote_downloaded_by", statistics, string, false),
    statRemoteOrigin("remote_origin", statistics, string, false),
    statRemotePath("remote_path", statistics, string, false),
    statRemoteId("remote_id", statistics, longInt, false),
    // properties
    propertyKey("key", properties, string, true),
    propertyValue("value", properties, string, true),
    propertyId("id", properties, longInt, false),
    propertyItemId("item_id", properties, longInt, false),
    // node meta infos
    itemInfosNodeId("item_id", itemInfos, longInt, true),
    itemInfosPropsModified("props_modified", itemInfos, date, true),
    itemInfosPropsModifiedBy("props_modified_by", itemInfos, string, true),
    itemInfosPropsMd5("props_md5", itemInfos, string, true),
    // builds
    moduleName("name", modules, string, true),
    moduleId("id", modules, longInt, false),
    buildDependencyName("name", dependencies, string, true),
    buildDependencyScope("scope", dependencies, string, true),
    buildDependencyType("type", dependencies, string, true),
    buildDependencySha1("sha1", dependencies, string, false),
    //buildDependencySha2("sha256", dependencies, string, false),
    buildDependencyMd5("md5", dependencies, string, false),
    buildDependencyId("id", dependencies, longInt, false),
    buildArtifactName("name", artifacts, string, true),
    buildArtifactType("type", artifacts, string, true),
    buildArtifactSha1("sha1", artifacts, string, false),
    //buildArtifactSha2("sha256", artifacts, string, false),
    buildArtifactMd5("md5", artifacts, string, false),
    buildArtifactId("id", artifacts, longInt, false),
    buildPropertyKey("key", buildProperties, string, true),
    buildPropertyValue("value", buildProperties, string, true),
    buildPropertyId("id", buildProperties, longInt, false),
    buildPromotionCreated("created", buildPromotions, date, true),
    buildPromotionCreatedBy("created_by", buildPromotions, string, true),
    buildPromotionStatus("status", buildPromotions, string, true),
    buildPromotionRepo("repo", buildPromotions, string, true),
    buildPromotionComment("comment", buildPromotions, string, true),
    buildPromotionUserName("user", buildPromotions, string, true),
    modulePropertyKey("key", moduleProperties, string, true),
    modulePropertyValue("value", moduleProperties, string, true),
    modulePropertyId("id", moduleProperties, longInt, false),
    buildUrl("url", builds, string, true),
    buildName("name", builds, string, true),
    buildNumber("number", builds, string, true),
    buildStarted("started", builds, date, true),
    buildRepo("repo", builds, string, true),
    buildCreated("created", builds, date, true),
    buildCreatedBy("created_by", builds, string, true),
    buildModified("modified", builds, date, true),
    buildModifiedBy("modified_by", builds, string, true),
    buildId("id", builds, longInt, false),
    // release bundles
    releaseBundleId("id", releaseBundles, longInt, true),
    releaseBundleName("name", releaseBundles, string, true),
    releaseBundleVersion("version", releaseBundles, string, true),
    releaseBundleStatus("status", releaseBundles, string, true),
    releaseBundleCreated("created", releaseBundles, date, true),
    releaseBundleSignature("signature", releaseBundles, string, true),
    releaseBundleType("type", releaseBundles, string, true),
    releaseBundleStoringRepo("storing_repo", releaseBundles, string, true),
    releaseBundleKeep("keep", releaseBundles, integer, true),
    releaseBundleSourceServiceId("source_service_id", releaseBundles, string, true),
    // release bundle files
    releaseBundleFileId("id", releaseBundleFiles, longInt, true),
    releaseBundleFileNodeId("item_id", releaseBundleFiles, longInt, true),
    releaseBundleFileBundleId("bundle_id", releaseBundleFiles, longInt, true),
    releaseBundleFileRepoPath("path", releaseBundleFiles, string, true),
    ;

    private final String signature;
    private final AqlDomainEnum domain;
    private final AqlVariableTypeEnum type;
    private final boolean defaultResultField;

    AqlPhysicalFieldEnum(String signature, AqlDomainEnum domain, AqlVariableTypeEnum type, boolean defaultResultField) {
        this.signature = signature;
        this.domain = domain;
        this.type = type;
        this.defaultResultField = defaultResultField;
    }

    @Override
    public String getSignature() {
        return signature;
    }

    @Override
    public AqlDomainEnum getDomain() {
        return domain;
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public boolean isId() {
        return ID_FIELDS.contains(this);
    }

    @Override
    public <T> T doSwitch(AqlFieldEnumSwitch<T> fieldEnumSwitch) {
        return fieldEnumSwitch.caseOf(this);
    }

    public AqlVariableTypeEnum getType() {
        return type;
    }

    public boolean isDefaultResultField() {
        return defaultResultField;
    }

    private static final Set<AqlPhysicalFieldEnum> ID_FIELDS = Collections.unmodifiableSet(EnumSet.of(
            itemId, statId, statRemoteId, propertyId, propertyItemId, moduleId, buildDependencyId, buildArtifactId,
            buildPropertyId,
            modulePropertyId, buildId, releaseBundleId, releaseBundleFileId, releaseBundleKeep));
    private static final AqlPhysicalFieldEnum[] EMPTY_ARRAY = new AqlPhysicalFieldEnum[0];
    private static final Map<AqlDomainEnum, AqlPhysicalFieldEnum[]> FIELDS_BY_DOMAIN;
    private static final List<AqlFieldEnum> propValues =
            List.of(propertyValue, buildPropertyValue, modulePropertyValue);
    private static final List<AqlFieldEnum> propKeys =
            List.of(propertyKey, buildPropertyKey, modulePropertyKey);

    static {
        //Collect and map fields by domain
        Map<AqlDomainEnum, List<AqlPhysicalFieldEnum>> map = Maps.newHashMap();
        for (AqlPhysicalFieldEnum field : values()) {
            List<AqlPhysicalFieldEnum> fields = map.computeIfAbsent(field.domain, k -> Lists.newArrayList());
            fields.add(field);
        }
        //Convert map value to array (immutable, no need to convert to array afterwards, etc.)
        Map<AqlDomainEnum, AqlPhysicalFieldEnum[]> mapOfArrays = Maps.newHashMap();
        for (Map.Entry<AqlDomainEnum, List<AqlPhysicalFieldEnum>> entry : map.entrySet()) {
            List<AqlPhysicalFieldEnum> fields = entry.getValue();
            mapOfArrays.put(entry.getKey(), fields.toArray(new AqlPhysicalFieldEnum[0]));
        }
        FIELDS_BY_DOMAIN = Collections.unmodifiableMap(mapOfArrays);
    }

    static AqlPhysicalFieldEnum[] getFieldsByDomain(AqlDomainEnum domain) {
        AqlPhysicalFieldEnum[] fields = FIELDS_BY_DOMAIN.get(domain);
        if (fields == null) {
            return EMPTY_ARRAY;
        }
        return fields;
    }

    public static boolean isKnownSignature(String signature) {
        signature = signature.toLowerCase();
        for (AqlPhysicalFieldEnum field : values()) {
            if (field.signature.equals(signature)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPropsKey(AqlFieldEnum field) {
        return propKeys.contains(field);
    }

    public static boolean isPropsValue(AqlFieldEnum field) {
        return propValues.contains(field);
    }

    public static boolean isProps(AqlFieldEnum field) {
        return isPropsKey(field) || isPropsValue(field);
    }
}
