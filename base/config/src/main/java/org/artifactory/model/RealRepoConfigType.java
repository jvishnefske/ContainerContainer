package org.artifactory.model;

import org.artifactory.repo.CdnRedirectRepoConfig;
import org.artifactory.repo.DownloadRedirectRepoConfig;
import org.jfrog.common.config.validation.NotUnicode;

import java.util.List;

/**
 * Interface represent real repository configurations
 *
 * @author shayb
 */
public interface RealRepoConfigType extends SpecificTypeRepositoryConfig {

    // TODO: 30/01/2022 move real repo type configuration methods to this interface

    boolean isDownloadRedirect();

    /**
     * @return true if the repo is configured to accept release artifacts, false otherwise
     */
    boolean isHandleReleases();

    void setHandleReleases(boolean handleReleases);

    /**
     * @return true if the repo is configured to accept snapshot artifacts, false otherwise
     */
    boolean isHandleSnapshots();

    void setHandleSnapshots(boolean handleSnapshots);

    /**
     * @return true is repo is blacked out
     */
    boolean isBlackedOut();

    void setBlackedOut(boolean blackedOut);

    /**
     * @return number of unique snapshots
     */
    int getMaxUniqueSnapshots();

    void setMaxUniqueSnapshots(int maxUniqueSnapshots);

    boolean isPriorityResolution();

    void setPriorityResolution(boolean priorityResolution);

    List<@NotUnicode String> getPropertySetRefs();

    void setPropertySetRefs(List<@NotUnicode String> propertyRefs);

    boolean isArchiveBrowsingEnabled();

    void setArchiveBrowsingEnabled(boolean archiveBrowsingEnabled);

    CdnRedirectRepoConfig getCdnRedirectRepoConfig();

    void setCdnRedirectRepoConfig(CdnRedirectRepoConfig cdnRedirectRepoConfig);

    DownloadRedirectRepoConfig getDownloadRedirectConfig();

    void setDownloadRedirectConfig(DownloadRedirectRepoConfig downloadRedirectConfig);
}
