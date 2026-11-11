package org.artifactory.repo.vcs;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.artifactory.descriptor.repo.vcs.VcsArtifactoryProviderConfiguration;
import org.artifactory.descriptor.repo.vcs.VcsBitbucketProviderConfiguration;
import org.artifactory.descriptor.repo.vcs.VcsCustomProviderConfiguration;
import org.artifactory.descriptor.repo.vcs.VcsGitHubEnterpriseProviderConfiguration;
import org.artifactory.descriptor.repo.vcs.VcsGitLabProviderConfiguration;
import org.artifactory.descriptor.repo.vcs.VcsGitProviderConfiguration;
import org.artifactory.descriptor.repo.vcs.VcsOldStashProviderConfiguration;
import org.artifactory.descriptor.repo.vcs.VcsProviderConfiguration;
import org.artifactory.descriptor.repo.vcs.VcsStashProviderConfiguration;
import org.artifactory.descriptor.repo.vcs.VcsGoogleSourceProviderConfiguration;
import org.jfrog.common.config.diff.DiffIgnore;

import jakarta.xml.bind.annotation.XmlEnumValue;
import java.util.Map;

/**
 * Enum of the VCS Git repository type.
 *
 * @author Tamar Rosen
 */
public enum VcsGitProvider {

    @XmlEnumValue("github")
    @JsonProperty("GITHUB")
    GITHUB("github", new VcsGitProviderConfiguration()),

    @XmlEnumValue("bitbucket")
    @JsonProperty("BITBUCKET")
    BITBUCKET("bitbucket", new VcsBitbucketProviderConfiguration()),

    @XmlEnumValue("stash")
    @JsonProperty("STASH")
    STASH("stash", new VcsStashProviderConfiguration()),

    @XmlEnumValue("oldstash")
    @JsonProperty("OLDSTASH")
    OLDSTASH("oldstash", new VcsOldStashProviderConfiguration()),

    @XmlEnumValue("artifactory")
    @JsonProperty("ARTIFACTORY")
    ARTIFACTORY("artifactory", new VcsArtifactoryProviderConfiguration()),

    @XmlEnumValue("gitlab")
    @JsonProperty("GITLAB")
    GITLAB("gitlab", new VcsGitLabProviderConfiguration()),

    @XmlEnumValue("githubenterprise")
    @JsonProperty("GITHUBENTERPRISE")
    GITHUBENTERPRISE("githubenterprise", new VcsGitHubEnterpriseProviderConfiguration()),

    @XmlEnumValue("googlesource")
    @JsonProperty("GOOGLESOURCE")
    GOOGLESOURCE("googlesource", new VcsGoogleSourceProviderConfiguration()),

    @XmlEnumValue("custom")
    @JsonProperty("CUSTOM")
    CUSTOM("custom", new VcsCustomProviderConfiguration());

    VcsGitProvider(String type, VcsProviderConfiguration configuration) {
        this.type = type;
        this.configuration = configuration;
    }

    private final String type;

    @DiffIgnore
    private final VcsProviderConfiguration configuration;

    public String getPrettyText() {
        return configuration.getPrettyText();
    }

    public String getRefsPrefix() {
        return configuration.getRefsPrefix();
    }

    public String getRepositoryDownloadUrl() {
        return configuration.getRepositoryDownloadUrl();
    }

    public String getResourceDownloadUrl() {
        return configuration.getResourceDownloadUrl();
    }

    public String getReleaseDownloadUrl() {
        return configuration.getReleaseDownloadUrl();
    }

    public Map<String, String> getHeaders() {
        return configuration.getHeaders();
    }

    public String getType() {
        return type;
    }

    /**
     * return VcsGitProvider if found from type ignore case, otherwise return null.
     * @param type
     * @return VcsGitProvider
     */
    public static VcsGitProvider fromString(String type) {
        for (VcsGitProvider vcsGitProvider : VcsGitProvider.values()) {
            if (vcsGitProvider.type.equalsIgnoreCase(type)) {
                return vcsGitProvider;
            }
        }
        return null;
    }
}