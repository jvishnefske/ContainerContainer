package org.artifactory.environment.converter.local.version.v7;

import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.version.CompoundVersionDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.artifactory.common.ConstantValues.allowAnyUpgrade;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v6100;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v6130;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v640;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v680;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v700m001;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tamir Hadad
 */
class V700UpgradePrerequisiteVerifierTest extends ArtifactoryHomeBoundJUnitTest {

    private V700UpgradePrerequisiteVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new V700UpgradePrerequisiteVerifier();
    }

    @Test
    void testInterested() {
        CompoundVersionDetails source = mock(CompoundVersionDetails.class);
        CompoundVersionDetails dest = mock(CompoundVersionDetails.class);
        when(source.getVersion()).thenReturn(v640.get());
        when(dest.getVersion()).thenReturn(v700m001.get());
        assertTrue(verifier.isInterested(homeStub, source, dest));
    }

    @Test
    void testNotInterested() {
        CompoundVersionDetails source = mock(CompoundVersionDetails.class);
        CompoundVersionDetails dest = mock(CompoundVersionDetails.class);
        when(source.getVersion()).thenReturn(v6100.get());
        when(dest.getVersion()).thenReturn(v700m001.get());
        assertFalse(verifier.isInterested(homeStub, source, dest));
        when(source.getVersion()).thenReturn(v680.get());
        when(dest.getVersion()).thenReturn(v6130.get());
        assertFalse(verifier.isInterested(homeStub, source, dest));
    }

    @Test
    void testBlockConvert() {
        homeStub.setProperty(allowAnyUpgrade, null);
        CompoundVersionDetails source = mock(CompoundVersionDetails.class);
        CompoundVersionDetails dest = mock(CompoundVersionDetails.class);
        assertThrows(IllegalStateException.class, () -> verifier.convert(homeStub, source, dest));
    }

    @Test
    void testAllowConvertByProp() {
        homeStub.setProperty(allowAnyUpgrade, v700m001.get().getVersion());
        CompoundVersionDetails source = mock(CompoundVersionDetails.class);
        CompoundVersionDetails dest = mock(CompoundVersionDetails.class);
        assertDoesNotThrow(() -> verifier.convert(homeStub, source, dest));
    }
}
