package org.artifactory.environment.converter.utils;

import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Tamir Hadad
 */
@Test
public class VersionAllowedUtilTest extends ArtifactoryHomeBoundTest {
    private static final Logger log = LoggerFactory.getLogger(VersionAllowedUtilTest.class);

    @BeforeMethod
    public void init() {
        homeStub.setProperty(ConstantValues.allowAnyUpgrade, "7.0.0-m001");
    }

    public void testAllowedUpgradeTest() {
        assertTrue(VersionAllowedUtil.isUpgradeAllowed(homeStub, ArtifactoryServiceVersionProvider.v700m001, log));
    }

    public void testForbiddenUpgradeTest() {
        homeStub.setProperty(ConstantValues.allowAnyUpgrade, "6.10.3");
        assertFalse(VersionAllowedUtil.isUpgradeAllowed(homeStub, ArtifactoryServiceVersionProvider.v700m001, log));
    }

    public void testNotExistVersionTest() {
        homeStub.setProperty(ConstantValues.allowAnyUpgrade, "6.10.3tttt");
        assertFalse(VersionAllowedUtil.isUpgradeAllowed(homeStub, ArtifactoryServiceVersionProvider.v700m001, log));
    }

    public void testFutureVersionTest() {
        homeStub.setProperty(ConstantValues.allowAnyUpgrade, "9.999.99");
        assertTrue(VersionAllowedUtil.isUpgradeAllowed(homeStub, ArtifactoryServiceVersionProvider.v700m001, log));
    }
}