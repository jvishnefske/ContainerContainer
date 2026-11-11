package org.artifactory.permissions;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

import static org.artifactory.common.ConstantValues.permissionsCacheType;

/**
 * @author Andrey Tashlitsky
 */
@Getter
@RequiredArgsConstructor
public enum PermissionsCacheType {

    LEGACY(Profiles.PERMISSIONS_CACHE_LEGACY),
    ACCESS(Profiles.PERMISSIONS_CACHE_ACCESS),
    SHADOW(Profiles.PERMISSIONS_CACHE_SHADOW);

    private final String profileName;

    public static PermissionsCacheType fromName(String permissionsCacheType) {
        try {
            return valueOf(permissionsCacheType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LEGACY;
        }
    }

    public static String toProfileName(String permissionsCacheType) {
        return fromName(permissionsCacheType).getProfileName();
    }

    public static boolean isLegacyCacheEnabled() {
        PermissionsCacheType cacheType = fromName(permissionsCacheType.getString());
        return cacheType == LEGACY || cacheType == SHADOW;
    }

    @UtilityClass
    public class Profiles {
        public final String PERMISSIONS_CACHE_LEGACY = "permissions-cache-legacy";
        public final String PERMISSIONS_CACHE_ACCESS = "permissions-cache-access";
        public final String PERMISSIONS_CACHE_SHADOW = "permissions-cache-shadow";
    }
}
