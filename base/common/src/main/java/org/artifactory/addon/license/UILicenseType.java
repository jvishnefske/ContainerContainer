package org.artifactory.addon.license;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Types of licenses returned to UI
 *
 * @author Yuval Reches
 */
@NoArgsConstructor
@AllArgsConstructor
public enum UILicenseType {
    NA("N/A"), COMMERCIAL_X, ENTERPRISE_X, ENTERPRISE_XWE, ENTERPRISE_PLUS_TRIAL_XWE, ENTERPRISE_PLUS_XWE, ARCHIVE;

    private String value;

    public String getValue() {
        return value;
    }
}
