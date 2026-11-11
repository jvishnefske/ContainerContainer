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
import jakarta.xml.bind.annotation.XmlTransient;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.descriptor.TaskDescriptor;
import org.artifactory.descriptor.property.PropertySet;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A non-serialized in-memory descriptor of local cache repositories.
 *
 * @author Fred Simon
 */
public class LocalCacheRepoDescriptor extends LocalRepoDescriptor implements TaskDescriptor {
    public static final String PATH_SUFFIX = "-cache";

    @XmlTransient
    private RemoteRepoDescriptor remoteRepo;

    public RemoteRepoDescriptor getRemoteRepo() {
        return remoteRepo;
    }

    public void setRemoteRepo(RemoteRepoDescriptor remoteRepo) {
        this.remoteRepo = remoteRepo;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public boolean isCache() {
        return true;
    }

    @Override
    public RepoType getType() {
        return remoteRepo.getType();
    }

    @Override
    public boolean isBlackedOut() {
        return remoteRepo.isBlackedOut();
    }

    @Override
    public String getIncludesPattern() {
        return remoteRepo.getIncludesPattern();
    }

    @Override
    public String getExcludesPattern() {
        return remoteRepo.getExcludesPattern();
    }

    @Override
    public boolean isHandleReleases() {
        return remoteRepo.isHandleReleases();
    }

    @Override
    public boolean isHandleSnapshots() {
        return remoteRepo.isHandleSnapshots();
    }

    @Override
    public int getMaxUniqueSnapshots() {
        return remoteRepo.getMaxUniqueSnapshots();
    }

    @Override
    public int getMaxUniqueTags() {
        return remoteRepo.getMaxUniqueTags();
    }

    @Override
    public List<PropertySet> getPropertySets() {
        return remoteRepo.getPropertySets();
    }

    @Override
    public void setPropertySets(List<PropertySet> propertySets) {
        remoteRepo.setPropertySets(propertySets);
    }

    @Override
    public boolean isPropertySetExists(String propertySetName) {
        return remoteRepo.isPropertySetExists(propertySetName);
    }

    @Override
    public void addPropertySet(PropertySet propertySet) {
        remoteRepo.addPropertySet(propertySet);
    }

    @Override
    public void updatePropertySet(PropertySet propertySet) {
        remoteRepo.updatePropertySet(propertySet);
    }

    @Override
    public PropertySet removePropertySet(String propertySetName) {
        return remoteRepo.removePropertySet(propertySetName);
    }

    @Override
    public PropertySet getPropertySet(String propertySetName) {
        return remoteRepo.getPropertySet(propertySetName);
    }

    @Override
    public boolean isArchiveBrowsingEnabled() {
        return remoteRepo.isArchiveBrowsingEnabled();
    }

    @Override
    public void setArchiveBrowsingEnabled(boolean archiveBrowsingEnabled) {
        remoteRepo.setArchiveBrowsingEnabled(archiveBrowsingEnabled);
    }

    @Override
    public boolean isForceNugetAuthentication() {
        return remoteRepo.isForceNugetAuthentication();
    }

    @Override
    public boolean isForceConanAuthentication() {
        return remoteRepo.isForceConanAuthentication();
    }

    @Override
    public void setForceNugetAuthentication(boolean forceNugetAuthentication) {
        // TODO: 28/02/2022 why? who updates the cache object?!
        remoteRepo.setForceNugetAuthentication(forceNugetAuthentication);
    }

    @Override
    public void setForceConanAuthentication(boolean forceConanAuthentication) {
        remoteRepo.setForceConanAuthentication(forceConanAuthentication);
    }

    @Override
    public boolean isForceP2Authentication() {
        return remoteRepo.isForceP2Authentication();
    }

    @Override
    public void setForceP2Authentication(boolean forceP2Authentication) {
        remoteRepo.setForceP2Authentication(forceP2Authentication);
    }

    @Override
    public boolean isDownloadRedirect() {
        return remoteRepo.isDownloadRedirect();
    }

    @Override
    public boolean isCdnRedirect() {
        return remoteRepo.isCdnRedirect();
    }

    @Override
    public void setDownloadRedirect(boolean downloadRedirect) {
        remoteRepo.setDownloadRedirect(downloadRedirect);
    }

    @Override
    public DownloadRedirectRepoConfigDescriptor getDownloadRedirectConfig() {
        return remoteRepo.getDownloadRedirectConfig();
    }

    @Override
    public void setCdnRedirect(boolean enableCdnRedirect) {
        remoteRepo.setCdnRedirect(enableCdnRedirect);
    }

    @Override
    @Nullable
    public CdnRedirectRepoConfigDescriptor getCdnRedirectRepoConfig() {
        return remoteRepo.getCdnRedirectRepoConfig();
    }

    @Override
    public boolean isCargoAnonymousAccess() {
        return remoteRepo.isCargoAnonymousAccess();
    }

    @Override
    public boolean isXrayIndex() {
        return remoteRepo.isXrayIndex();
    }

    @Override
    public void setXrayIndex(boolean xrayIndex) {
        remoteRepo.setXrayIndex(xrayIndex);
    }

    @Override
    public int getXrayDataTtl() {
        return remoteRepo.getXrayDataTtl();
    }

    @Override
    public void setXrayDataTtl(int xrayDataTtl) {
        remoteRepo.setXrayDataTtl(xrayDataTtl);
    }

    @Override
    public void setXrayNotAvailableMode(XrayNotAvailableMode xrayNotAvailableMode) {
        remoteRepo.setXrayNotAvailableMode(xrayNotAvailableMode);
    }

    @Override
    public XrayNotAvailableMode getXrayNotAvailableMode() {
        return remoteRepo.getXrayNotAvailableMode();
    }

    @Override
    public XrayRepoConfigDescriptor getXrayConfig() {
        return remoteRepo.getXrayConfig();
    }

    @Override
    public boolean sameTaskDefinition(TaskDescriptor otherDescriptor) {
        if (otherDescriptor == null || !(otherDescriptor instanceof LocalCacheRepoDescriptor)) {
            throw new IllegalArgumentException("Cannot compare backup dexcriptor " + this + " with " + otherDescriptor);
        }
        LocalCacheRepoDescriptor localCacheRepoDesc = (LocalCacheRepoDescriptor) otherDescriptor;
        return localCacheRepoDesc.isBlackedOut() == this.isBlackedOut() &&
                localCacheRepoDesc.getKey().equals(this.getKey()) &&
                localCacheRepoDesc.remoteRepo.getUnusedArtifactsCleanupPeriodHours() ==
                        this.remoteRepo.getUnusedArtifactsCleanupPeriodHours();
    }

    public long getRetrievalCachePeriodMillis() {
        return TimeUnit.SECONDS.toMillis(remoteRepo.getRetrievalCachePeriodSecs());
    }
}