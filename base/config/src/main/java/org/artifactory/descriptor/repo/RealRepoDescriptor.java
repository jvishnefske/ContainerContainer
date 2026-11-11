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

package org.artifactory.descriptor.repo;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlIDREF;
import jakarta.xml.bind.annotation.XmlType;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.util.AlreadyExistsException;
import org.artifactory.util.DoesNotExistException;
import org.jfrog.common.config.diff.DiffAtomic;
import org.jfrog.common.config.diff.DiffElement;
import org.jfrog.common.config.diff.DiffReference;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.artifactory.descriptor.repo.XrayRepoConfigDescriptor.DEFAULT_DATA_TTL;
import static org.artifactory.repo.xray.XrayNotAvailableMode.USE_SYSTEM_PROPERTY;

/**
 * @author Fred Simon
 */
@XmlType(name = "RealRepoType", propOrder = {"blackedOut", "handleReleases", "handleSnapshots", "maxUniqueSnapshots",
        "maxUniqueTags", "blockPushingSchema1", "suppressPomConsistencyChecks", "propertySets",
        "archiveBrowsingEnabled", "xrayConfig",
        "downloadRedirectConfig", "priorityResolution", "cdnRedirectRepoConfig"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
public abstract class RealRepoDescriptor extends RepoBaseDescriptor {

    @XmlElement(defaultValue = "false", required = false)
    private boolean blackedOut;

    @XmlElement(defaultValue = "true", required = false)
    private boolean handleReleases = true;

    @XmlElement(defaultValue = "true", required = false)
    private boolean handleSnapshots = true;

    @XmlElement(defaultValue = "0", required = false)
    private int maxUniqueSnapshots;

    @XmlElement(defaultValue = "0", required = false)
    private int maxUniqueTags;

    @XmlElement(defaultValue = "true", required = false)
    private boolean blockPushingSchema1 = true;

    @XmlElement(defaultValue = "true", required = false)
    private boolean suppressPomConsistencyChecks = true;

    @XmlElement(defaultValue = "false", required = false)
    private boolean archiveBrowsingEnabled;

    @XmlElement(name = "xray", required = false)
    @DiffElement(name = "xray")
    private XrayRepoConfigDescriptor xrayConfig;

    @XmlElement(name = "downloadRedirect", required = false)
    @DiffElement(name = "downloadRedirect")
    private DownloadRedirectRepoConfigDescriptor downloadRedirectConfig;

    @XmlElement(defaultValue = "false", required = false)
    private boolean priorityResolution;

    @XmlElement(name = "cdnRedirect", required = false)
    @DiffElement(name = "cdnRedirect")
    private CdnRedirectRepoConfigDescriptor cdnRedirectRepoConfig;

    @XmlIDREF
    @XmlElementWrapper(name = "propertySets")
    @XmlElement(name = "propertySetRef", type = PropertySet.class, required = false)
    @DiffAtomic
    @DiffReference
    @Valid
    private List<PropertySet> propertySets = new ArrayList<>();

    public boolean isHandleReleases() {
        return handleReleases;
    }

    public void setHandleReleases(boolean handleReleases) {
        this.handleReleases = handleReleases;
    }

    public boolean isHandleSnapshots() {
        return handleSnapshots;
    }

    public void setHandleSnapshots(boolean handleSnapshots) {
        this.handleSnapshots = handleSnapshots;
    }


    public boolean isBlackedOut() {
        return blackedOut;
    }

    public void setBlackedOut(boolean blackedOut) {
        this.blackedOut = blackedOut;
    }

    public int getMaxUniqueSnapshots() {
        return maxUniqueSnapshots;
    }

    public void setMaxUniqueSnapshots(int maxUniqueSnapshots) {
        this.maxUniqueSnapshots = maxUniqueSnapshots;
    }

    public int getMaxUniqueTags() {
        return maxUniqueTags;
    }

    public void setMaxUniqueTags(int maxUniqueTags) {
        this.maxUniqueTags = maxUniqueTags;
    }

    public boolean isBlockPushingSchema1() {
        return blockPushingSchema1;
    }

    public void setBlockPushingSchema1(boolean blockPushingSchema1) {
        this.blockPushingSchema1 = blockPushingSchema1;
    }

    public boolean isSuppressPomConsistencyChecks() {
        return suppressPomConsistencyChecks;
    }

    public void setSuppressPomConsistencyChecks(boolean suppressPomConsistencyChecks) {
        this.suppressPomConsistencyChecks = suppressPomConsistencyChecks;
    }

    public List<PropertySet> getPropertySets() {
        return propertySets;
    }

    public void setPropertySets(List<PropertySet> propertySets) {
        this.propertySets = Optional.ofNullable(propertySets).orElseGet(ArrayList::new);
    }

    public boolean isPropertySetExists(String propertySetName) {
        return getPropertySet(propertySetName) != null;
    }

    public void addPropertySet(PropertySet propertySet) {
        String propertySetName = propertySet.getName();
        if (isPropertySetExists(propertySetName)) {
            throw new AlreadyExistsException("Property set " + propertySetName + " already exists");
        }
        propertySets.add(propertySet);
    }

    public void updatePropertySet(PropertySet propertySet) {
        int index = propertySets.indexOf(propertySet);
        if (index == -1) {
            throw new DoesNotExistException("Property set " + propertySet.getName() + " does not exist");
        }
        propertySets.set(index, propertySet);
    }

    public PropertySet removePropertySet(String propertySetName) {
        PropertySet propertySet = getPropertySet(propertySetName);
        if (propertySet == null) {
            return null;
        }
        //Remove the property set from the property sets list
        while (propertySets.contains(propertySet)) {
            boolean removed = propertySets.remove(propertySet);
            //Sanity check
            if (!removed) {
                throw new RuntimeException("Property set: '" + propertySet.getName() + "' could be removed from " +
                        "repository: '" + getKey() + "'");
            }
        }
        return propertySet;
    }

    public PropertySet getPropertySet(String propertySetName) {
        for (PropertySet propertySet : propertySets) {
            if (propertySet.getName().equals(propertySetName)) {
                return propertySet;
            }
        }
        return null;
    }

    public boolean isArchiveBrowsingEnabled() {
        return archiveBrowsingEnabled;
    }

    public void setArchiveBrowsingEnabled(boolean archiveBrowsingEnabled) {
        this.archiveBrowsingEnabled = archiveBrowsingEnabled;
    }

    public boolean isXrayIndex() {
        return xrayConfig != null && xrayConfig.isEnabled();
    }

    public void setXrayIndex(boolean xrayIndex) {
        if (xrayConfig == null) {
            xrayConfig = new XrayRepoConfigDescriptor();
        }
        xrayConfig.setEnabled(xrayIndex);
    }

    public XrayNotAvailableMode getXrayNotAvailableMode() {
        if (xrayConfig != null) {
            return xrayConfig.getXrayNotAvailableMode();
        }
        return USE_SYSTEM_PROPERTY;
    }

    public void setXrayNotAvailableMode(XrayNotAvailableMode xrayNotAvailableMode) {
        if (xrayConfig == null) {
            xrayConfig = new XrayRepoConfigDescriptor();
        }
        xrayConfig.setXrayNotAvailableMode(xrayNotAvailableMode);
    }

    public int getXrayDataTtl() {
        if (xrayConfig != null) {
            return xrayConfig.getDataTtl();
        }
        return DEFAULT_DATA_TTL;
    }

    public void setXrayDataTtl(int xrayDataTtl) {
        if (xrayConfig == null) {
            xrayConfig = new XrayRepoConfigDescriptor();
        }
        xrayConfig.setDataTtl(xrayDataTtl);
    }

    public XrayRepoConfigDescriptor getXrayConfig() {
        return xrayConfig;
    }

    public void setXrayConfig(XrayRepoConfigDescriptor xrayConfig) {
        this.xrayConfig = xrayConfig;
    }

    public boolean isDownloadRedirect() {
        return downloadRedirectConfig != null && downloadRedirectConfig.isEnabled();
    }

    public void setDownloadRedirect(boolean enableRedirect) {
        if (downloadRedirectConfig == null) {
            downloadRedirectConfig = new DownloadRedirectRepoConfigDescriptor();
        }
        downloadRedirectConfig.setEnabled(enableRedirect);
    }

    public DownloadRedirectRepoConfigDescriptor getDownloadRedirectConfig() {
        return downloadRedirectConfig;
    }

    public void setDownloadRedirectConfig(DownloadRedirectRepoConfigDescriptor downloadRedirect) {
        this.downloadRedirectConfig = downloadRedirect;
    }

    public boolean isCdnRedirect() {
        return cdnRedirectRepoConfig != null && cdnRedirectRepoConfig.isEnabled();
    }

    public void setCdnRedirect(boolean enableCdnRedirect) {
        if (cdnRedirectRepoConfig == null) {
            cdnRedirectRepoConfig = new CdnRedirectRepoConfigDescriptor();
        }
        cdnRedirectRepoConfig.setEnabled(enableCdnRedirect);
    }

    @Nullable
    public CdnRedirectRepoConfigDescriptor getCdnRedirectRepoConfig() {
        return cdnRedirectRepoConfig;
    }

    public void setCdnRedirectConfig(@Nullable CdnRedirectRepoConfigDescriptor cdnRedirect) {
        this.cdnRedirectRepoConfig = cdnRedirect;
    }

    public boolean isPriorityResolution() {
        return priorityResolution;
    }

    public void setPriorityResolution(boolean priorityResolution) {
        this.priorityResolution = priorityResolution;
    }

    @Override
    public boolean isReal() {
        return true;
    }

    public abstract boolean isLocal();

    public abstract boolean isCache();
}