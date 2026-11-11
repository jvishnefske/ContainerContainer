package org.artifactory.environment.converter.local;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;

/**
 * @author Uriah Levy
 */
@Test
public class HomeConverterAdapterTest extends ArtifactoryHomeBoundTest {

    public void testIsInterestedTrue() {
        HomeConverterAdapter homeConverterAdapter = new HomeConverterAdapter(ArtifactoryHome.get());
        CompoundVersionDetails from = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v4120.get(), null, 0L);
        CompoundVersionDetails to = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v6170.get(), null, 0L);
        assertTrue(homeConverterAdapter.isInterested(from, to));
    }

    public void testIsInterestedFalse() {
        HomeConverterAdapter homeConverterAdapter = new HomeConverterAdapter(ArtifactoryHome.get());
        CompoundVersionDetails from = new CompoundVersionDetails(
                ArtifactoryServiceVersion.getCurrent(), null, 0L);
        CompoundVersionDetails to = new CompoundVersionDetails(
                ArtifactoryServiceVersion.getCurrent(), null, 0L);
        assertFalse(homeConverterAdapter.isInterested(from, to));
    }
}