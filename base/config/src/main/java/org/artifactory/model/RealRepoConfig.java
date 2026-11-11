package org.artifactory.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.util.AlreadyExistsException;

/**
 * Interface represent the real (non-virtual) repository config
 */
public interface RealRepoConfig extends RepoConfig {

    @Override
    RealRepoConfigType getRepoTypeConfig();

    @Override
    default boolean isReal() {
        return true;
    }

    /**
     * @return true if the repo is local, false otherwise
     */
    @JsonIgnore
    boolean isLocal();

    default boolean isPropertySetExists(String propertySetRef) {
        return getRepoTypeConfig().getPropertySetRefs().contains(propertySetRef);
    }

    default void addPropertySetRef(String propertySetRef) {
        if (isPropertySetExists(propertySetRef)) {
            throw new AlreadyExistsException("Property set " + propertySetRef + " already exists");
        }
        getRepoTypeConfig().getPropertySetRefs().add(propertySetRef);
    }

    @JsonIgnore
    @Override
    default XrayNotAvailableMode getXrayNotAvailableMode() {
        return getSecurityConfig().getXrayNotAvailableMode();
    }
}
