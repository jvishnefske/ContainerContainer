package org.artifactory.storage;

import org.artifactory.common.ConstantValues;
import org.jfrog.storage.config.BinaryStoreConstValues;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.artifactory.common.ConstantValues.cargoGitIndexEnabled;
import static org.artifactory.common.ConstantValues.gemsVirtualSkipSortingPackageInfoFromOneSubRepo;

/**
 * @author TamirH
 */
class ConstantValuesTest {

    @Test
    void testBinaryStoreConstValuesCompatability() {
        for (BinaryStoreConstValues binaryStoreConstValue : BinaryStoreConstValues.values()) {
            Assertions.assertNotNull(ConstantValues.valueOf(binaryStoreConstValue.getPropertyName()));
        }
    }

    @Test
    void testUseRangeForReplacedGetDisabled() {
        var value = ConstantValues.valueOf("useRangeForReplacedGet");
        Assertions.assertEquals("false", value.getDefValue());
    }

    @Test
    void testGemsVirtualSkipSortingPackageInfoFromOneSubRepo(){
        Assertions.assertFalse(Boolean.parseBoolean(gemsVirtualSkipSortingPackageInfoFromOneSubRepo.getDefValue()));
    }

    /**
     * The legacy Cargo Git index is deprecated and must stay disabled by default (existing Git-index repos should
     * stop being served unless an operator explicitly opts back in via this property).
     */
    @Test
    void testCargoGitIndexEnabledDisabledByDefault() {
        Assertions.assertEquals("artifactory.cargo.git.index.enabled", cargoGitIndexEnabled.getPropertyName());
        Assertions.assertFalse(Boolean.parseBoolean(cargoGitIndexEnabled.getDefValue()));
    }
}