package org.artifactory.curation;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.request.ArtifactoryHeaders;

import java.util.Map;

/**
 * Utilities for CVS pass-through header detection.
 */
@UtilityClass
public class CurationPassThroughUtils {

    public static boolean isPassThroughHeaderPresent(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        String value = headers.entrySet().stream()
                .filter(entry -> ArtifactoryHeaders.PASS_THROUGH_KEY.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        return StringUtils.isNotBlank(value) && BooleanUtils.toBoolean(value);
    }
}
