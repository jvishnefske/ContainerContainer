package org.artifactory.version;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author gidis
 */
public class ArtifactoryServiceVersionImpl implements ArtifactoryServiceVersion {

    static final Pattern VERSION_PATTERN = Pattern
            .compile("(?<major>\\d)\\.(?<minor>\\d{1,3})\\.(?<patch>\\d{1,2})(-(?<rev>[mp]\\d{3}))?");
    static final int RELEASE_REVISION = 900;

    private final String version;
    private final long revision;
    private final ArtifactoryServiceVersionProvider provider;


    public ArtifactoryServiceVersionImpl(String version, long revision, ArtifactoryServiceVersionProvider provider) {
        this.version = version;
        this.revision = revision;
        this.provider = provider;
    }

    @Override
    public ArtifactoryServiceVersionProvider getProvider() {
        return provider;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public long getRevision() {
        return revision;
    }

    @Override
    public boolean isCurrent() {
        return ArtifactoryServiceVersion.getCurrent().equals(this);
    }

    @Override
    public boolean before(ArtifactoryServiceVersion otherVersion) {
        return otherVersion.getRevision() > this.revision;
    }

    @Override
    public boolean after(ArtifactoryServiceVersion otherVersion) {
        return otherVersion.getRevision() < this.revision;
    }

    @Override
    public boolean beforeOrEqual(ArtifactoryServiceVersion otherVersion) {
        return otherVersion.getRevision() >= this.revision;
    }

    @Override
    public boolean afterOrEqual(ArtifactoryServiceVersion otherVersion) {
        return otherVersion.getRevision() <= this.revision;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ArtifactoryServiceVersionImpl that)) {
            return false;
        }
        return revision == that.revision &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, revision);
    }

    @Override
    public String toString() {
        return version;
    }
}