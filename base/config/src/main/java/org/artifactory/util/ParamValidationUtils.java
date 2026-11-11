package org.artifactory.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Mor Merhav
 */
@UtilityClass
public class ParamValidationUtils {

    public String requireNonBlank(String str, String errorMessage) {
        if (StringUtils.isBlank(str)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return str;
    }
}
