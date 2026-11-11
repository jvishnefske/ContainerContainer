package org.artifactory.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.jfrog.common.platform.StringBasedEnum;

/**
 * @author Alexei Vainshtein
 */
@UtilityClass
public class ReloadablePropertiesUtil {

    public static <T extends Enum<T> & StringBasedEnum> T getEnumValue(Class<T> enumType, String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }

        T[] enumConstants = enumType.getEnumConstants();
        for (T enumInstance : enumConstants) {
            if (enumInstance.getString().equals(value)) {
                return enumInstance;
            }
        }

        throw new IllegalArgumentException(
                "No enum value " + enumType.getCanonicalName() + "." + value);
    }
}
