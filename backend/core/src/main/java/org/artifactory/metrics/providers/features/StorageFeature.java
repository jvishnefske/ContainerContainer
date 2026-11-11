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

package org.artifactory.metrics.providers.features;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.callhome.FeatureGroup;
import org.artifactory.api.storage.StorageQuotaInfo;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.util.CollectionUtils;
import org.jfrog.storage.binstore.BinariesInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryTreeElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.jfrog.common.FileUtils.bytesToMB;


/**
 * This class represent the storage feature group of the CallHome feature (see RTFACT-9621)
 *
 * @author nadavy
 */
@Component
@Slf4j
public class StorageFeature implements CallHomeFeature {

    @Autowired
    private StorageService storageService;
    @Autowired
    private StorageSummaryService storageSummaryService;

    private final long DEFAULT_VALUE = -1L;

    @Override
    public FeatureGroup getFeature() {
        BinaryTreeElement<Map<String, String>> binaryTreeRootElement = storageService
                .getFullBinaryProviderInfo().rootTreeElement;
        FeatureGroup storageFeature = new FeatureGroup("storage");
        FeatureGroup chainFeature = new FeatureGroup("chain");
        addStorageSummaryFeature(storageFeature, binaryTreeRootElement);
        addStorageThresholdFeature(storageFeature);
        addStorageMountsFeature(storageFeature, binaryTreeRootElement, this::addMountFeature);
        addStorageChainSummary(chainFeature, binaryTreeRootElement);
        storageFeature.addFeature(chainFeature);
        return storageFeature;
    }

    @VisibleForTesting
    void addStorageChainSummary(FeatureGroup feature,
            BinaryTreeElement<Map<String, String>> binaryTreeRootElement) {
        boolean advance = true;
        String parentId = "";
        while (advance) {
            addStorageWithMountsFeature(feature, binaryTreeRootElement, parentId ,this::addChainFeature);
            if (binaryTreeRootElement.hasNext()) {
                parentId = binaryTreeRootElement.getData().get("id");
                binaryTreeRootElement = binaryTreeRootElement.getNextBinaryTreeElement();
            } else {
                advance = false;
            }
        }
    }

    /**
     * Add general storage summary
     */
    private void addStorageSummaryFeature(FeatureGroup storageFeature,
            BinaryTreeElement<Map<String, String>> binaryTreeRootElement) {
        FeatureGroup summaryFeature = new FeatureGroup("summary");
        String storageType = binaryTreeRootElement.getData().get("type");
        summaryFeature.addFeatureAttribute("provider", storageType);
        if ("sharding".equals(storageType)) {
            int mounts = binaryTreeRootElement.getSubBinaryTreeElements().size();
            summaryFeature.addFeatureAttribute("mounts", mounts);
            String redundancy = binaryTreeRootElement.getData().get("redundancy");
            summaryFeature.addFeatureAttribute("redundancy", Integer.parseInt(redundancy));
        }
        try {
            StorageSummaryInfo storageSummaryInfo = storageSummaryService.getLocalCache();
            BinariesInfo binariesInfo = storageSummaryInfo.getBinariesInfo();
            summaryFeature.addFeatureAttribute("binaries_count", binariesInfo.getBinariesCount());
            summaryFeature.addFeatureAttribute("artifacts_count", storageSummaryInfo.getTotalFiles());
            summaryFeature.addFeatureAttribute("items_count", storageSummaryInfo.getTotalItems());
            summaryFeature.addFeatureAttribute("binaries_size", bytesToMB(binariesInfo.getBinariesSize()));
            summaryFeature.addFeatureAttribute("artifacts_size", bytesToMB(storageSummaryInfo.getTotalSize()));
            // percentage
            summaryFeature.addFeatureAttribute("optimization", storageSummaryInfo.getOptimization() * 100);
            summaryFeature.addFeatureAttribute("summary_timestamp", storageSummaryInfo.getLastUpdated());
        } catch (CacheUnAvailableException e) {
            log.warn("Summary info is not available yet, possibly due to restart");
        }
        storageFeature.addFeature(summaryFeature);
    }

    /**
     * Add limit and warning thresholds (if available)
     */
    private void addStorageThresholdFeature(FeatureGroup storageFeature) {
        StorageQuotaInfo storageQuotaInfo = storageService.getStorageQuotaInfo(0);
        if (storageQuotaInfo != null) {
            FeatureGroup thresholdsFeature = new FeatureGroup("thresholds");
            thresholdsFeature.addFeatureAttribute("warning", storageQuotaInfo.getDiskSpaceWarningPercentage());
            thresholdsFeature.addFeatureAttribute("limit", storageQuotaInfo.getDiskSpaceLimitPercentage());
            storageFeature.addFeature(thresholdsFeature);
        }
    }

    /**
     * Add storage info for each mount (only on root level), or just the root if not available
     */
    private FeatureGroup addStorageMountsFeature(FeatureGroup storageFeature,
            BinaryTreeElement<Map<String, String>> binaryTreeRootElement,
            Function<BinaryTreeElement<Map<String, String>>, FeatureGroup> storageFeatureFiller) {
        List<BinaryTreeElement<Map<String, String>>> binaryTreeElements = binaryTreeRootElement
                .getSubBinaryTreeElements();
        if (CollectionUtils.isNullOrEmpty(binaryTreeElements)) {
            FeatureGroup mountFeature = storageFeatureFiller.apply(binaryTreeRootElement);
            storageFeature.addFeature(mountFeature);
        } else {
            // add storage info for each mount
            binaryTreeElements.stream()
                    .map(storageFeatureFiller::apply)
                    .forEach(storageFeature::addFeature);
        }

        return storageFeature;
    }

    private void addStorageWithMountsFeature(FeatureGroup storageFeature,
            BinaryTreeElement<Map<String, String>> binaryTreeRootElement, String parentId,
            BiFunction<BinaryTreeElement<Map<String, String>>, String, FeatureGroup> storageFeatureFiller) {
        FeatureGroup mountFeature = storageFeatureFiller.apply(binaryTreeRootElement, parentId);
        storageFeature.addFeature(mountFeature);
        List<BinaryTreeElement<Map<String, String>>> binaryTreeElements = binaryTreeRootElement
                .getSubBinaryTreeElements();
        if (!CollectionUtils.isNullOrEmpty(binaryTreeElements)) {
            String parentIdForMount = binaryTreeRootElement.getData().get("id");
            // add storage info for each mount
            binaryTreeElements.stream()
                    .map(element -> storageFeatureFiller.apply(element, parentIdForMount))
                    .forEach(storageFeature::addFeature);
        }
    }

    private FeatureGroup addMountFeature(BinaryTreeElement<Map<String, String>> element) {
        Map<String, String> binaryProviderData = element.getData();
        FeatureGroup summaryFeature = new FeatureGroup("mount " + binaryProviderData.get("id"));
        summaryFeature.addFeatureAttribute("label", binaryProviderData.get("id"));
        summaryFeature.addFeatureAttribute("type", binaryProviderData.get("type"));
        summaryFeature.addFeatureAttribute("used", getAttributeSizeInMb(binaryProviderData, "usageSpace"));
        summaryFeature.addFeatureAttribute("available", getAttributeSizeInMb(binaryProviderData, "freeSpace"));
        summaryFeature.addFeatureAttribute("size", getAttributeSizeInMb(binaryProviderData, "totalSpace"));

        return summaryFeature;
    }

    private FeatureGroup addChainFeature(BinaryTreeElement<Map<String, String>> element, String parentId) {
        Map<String, String> binaryProviderData = element.getData();
        FeatureGroup summaryFeature = new FeatureGroup(binaryProviderData.get("id"));
        summaryFeature.addFeatureAttribute("parentId", parentId);
        summaryFeature.addFeatureAttribute("label", binaryProviderData.get("id"));

        Map<String, String> whitelistedData = StorageFeatureWhitelist.whitelist(binaryProviderData);
        whitelistedData.forEach(summaryFeature::addFeatureAttribute);

        return summaryFeature;
    }

    @VisibleForTesting
    long getAttributeSizeInMb(Map<String, String> binaryProviderData, String attribute) {
        String value = binaryProviderData.get(attribute);
        long size = StringUtils.isNumeric(value) ? Long.parseLong(value) : DEFAULT_VALUE;

        return bytesToMB(size);
    }

}

