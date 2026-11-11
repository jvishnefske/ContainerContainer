package org.artifactory.model.fs;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;

/**
 * @author Oleksandr Legenkiy
 */
@UtilityClass
@Slf4j
public class ItemInfoUtils {

    private static final String UPDATE_TIMESTAMP_SYS_PROP_KEY = "artifactory.sys.update.file.created.timestamp";

    public static boolean isUpdateCreatedTimestampOnCopyOrMoveEnabled() {
        try {
            return ConstantValues.updateFileCreatedTimestampOnCopyOrMove.getBoolean();
        } catch (Exception e) {
            log.debug("Failed to retrieve property value from ConstantValues. Trying to obtain the value from system" +
                      " properties...", e);
            return Boolean.parseBoolean(System.getProperty(UPDATE_TIMESTAMP_SYS_PROP_KEY, "false"));
        }
    }
}
