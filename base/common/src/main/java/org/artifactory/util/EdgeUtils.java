package org.artifactory.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.entitlement.EntitlementType;

/**
 * @author Hussein Alshqerat
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EdgeUtils {
    public static final String NOT_SUPPORTED_FOR_EDGE = "Feature is not supported on edge server.";

    public static boolean isEdgeLicensed() {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        return addonsManager.isEntitled(EntitlementType.EDGE);
    }
}