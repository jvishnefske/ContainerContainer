package org.artifactory.repo.vcs;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.xml.bind.annotation.XmlEnumValue;

/**
 * Enum of the VCS type support for remote repositories.
 *
 * @author Tamar Rosen
 */
public enum VcsType {
    @XmlEnumValue("git") @JsonProperty("GIT") GIT("git");

    private final String val;

    VcsType(String val) {
        this.val = val;
    }

    @Override
    public String toString() {
        return val;
    }

    public String getVal() {
        return val;
    }

    /**
     * return VcsType if found from val ignore case, otherwise return null.
     * @param val
     * @return VcsType
     */
    public static VcsType fromString(String val) {
        for (VcsType vcsType : VcsType.values()) {
            if (vcsType.val.equalsIgnoreCase(val)) {
                return vcsType;
            }
        }
        return null;
    }
}