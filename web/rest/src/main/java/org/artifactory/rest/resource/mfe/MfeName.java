package org.artifactory.rest.resource.mfe;

/**
 * Enum representing all supported MFE project names.
 * Provides type safety for MFE name references throughout the codebase.
 *
 * @author michaelku
 */
public enum MfeName {
    /**
     * Core MFE project (UMD format) - serves files from root webapp directory.
     */
    CORE("core"),

    /**
     * RTUI MFE project (ESM format) - serves files from /rtui subdirectory.
     */
    RTUI("rtui"),

    /**
     * BUILDINFOMFE MFE project (ESM format) - serves files from /buildinfomfe subdirectory.
     */
    BUILDINFOMFE("buildinfomfe"),

    /**
     * RETENTION MFE project (UMD format) - serves files from /retention subdirectory.
     */
    RETENTION("retention"),

    /**
     * PACKAGES MFE project (ESM format) - serves files from /packages subdirectory.
     */
    PACKAGES("packages"),

    /**
     * RELEASEBUNDLES MFE project (ESM format) - serves files from /releasebundles subdirectory.
     */
    RELEASEBUNDLES("releasebundles"),

    /**
     * RBV1RECEIVED MFE project (ESM format) - serves files from /rbv1received subdirectory.
     */
    RBV1RECEIVED("rbv1received");

    private final String value;

    MfeName(String value) {
        this.value = value;
    }

    /**
     * Gets the string value of the MFE name.
     * @return The string representation (e.g., "core", "rtui")
     */
    public String getValue() {
        return value;
    }

    /**
     * Parses a string value to an MfeName enum.
     * @param value The string value (e.g., "core", "rtui")
     * @return The corresponding MfeName enum, or null if not found
     */
    public static MfeName fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (MfeName mfeName : values()) {
            if (mfeName.value.equals(value)) {
                return mfeName;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return value;
    }
}

