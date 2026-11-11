package org.artifactory.addon.curation;

import java.util.List;

/**
 * @author Tomer Slutsky
 *
 * Note: indexVersionsCompliantUrls is not used anymore, as part of RTDEV-69501.
 */
public record CurationSupportedPackageType(String packageType, List<String> supportedUrls,
                                           List<String> indexVersionsCompliantUrls) {
}
