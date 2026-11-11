package org.artifactory.addon;

import org.artifactory.common.profile.PackageProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Volodymyr Shulga
 */
class PackageProfileTest {

    @Test
    void packageProfileNull() {
        Assertions.assertThrows(NullPointerException.class, () -> PackageProfile.toPackageProfile(null));
    }

    @Test
    void packageProfileExisting() {
        final String packageProfile = PackageProfile.toPackageProfile("NUGET");
        Assertions.assertEquals(PackageProfile.NEW_NUGET_IMPL, packageProfile);
    }

    @Test
    void packageProfileNonExisting() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> PackageProfile.toPackageProfile("NON_EXISTING_PACKAGE"));
    }
}