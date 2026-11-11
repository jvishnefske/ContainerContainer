package org.artifactory.lifecycle.converter.postinit.v119;

import org.artifactory.api.config.FederatedRepoConfigEnrichmentService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FederatedBlobEnrichmentPostInitConverterTest extends ArtifactoryHomeBoundTest {

    private FederatedRepoConfigEnrichmentService enrichmentService;

    @BeforeMethod
    public void setUp() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        enrichmentService = mock(FederatedRepoConfigEnrichmentService.class);
        SecurityService securityService = mock(SecurityService.class);
        when(context.beanForType(FederatedRepoConfigEnrichmentService.class)).thenReturn(enrichmentService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        // run the doAsSystem runnable inline so we can assert the delegation
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(securityService).doAsSystem(any());
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void convertDelegatesToServiceAsSystem() {
        FederatedBlobEnrichmentPostInitConverter converter =
                new FederatedBlobEnrichmentPostInitConverter(null);
        CompoundVersionDetails source = mock(CompoundVersionDetails.class);

        converter.convert(source, source);

        verify(enrichmentService).enrichColdFederatedReposBlobs();
    }

    @Test
    public void isInterestedOnlyWhenCrossingIntoTargetVersion() {
        FederatedBlobEnrichmentPostInitConverter converter =
                new FederatedBlobEnrichmentPostInitConverter(ArtifactoryServiceVersionProvider.v71570.get());

        CompoundVersionDetails belowTarget = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v71480.get(), "build", 1);
        CompoundVersionDetails stillBelowTarget = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v71500.get(), "build", 1);
        CompoundVersionDetails atTarget = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v71570.get(), "build", 1);

        // target still below converter version -> skip
        assertFalse(converter.isInterested(belowTarget, stillBelowTarget));
        // crossing into the target version -> run
        assertTrue(converter.isInterested(stillBelowTarget, atTarget));
        // source already at target version, target also at target version -> run (retry after prior failure)
        assertTrue(converter.isInterested(atTarget, atTarget));
    }

    @Test
    public void isInterestedRetryWhenSourceAtExactTargetVersion() {
        // Registration shape used in PostInitVersions: from == until == v71570.
        // A node whose enrichment failed on a previous boot stays at v71570.
        // Without beforeOrEqual the converter would be silently skipped on every
        // subsequent upgrade; with beforeOrEqual it correctly re-runs.
        FederatedBlobEnrichmentPostInitConverter converter =
                new FederatedBlobEnrichmentPostInitConverter(ArtifactoryServiceVersionProvider.v71570.get());

        CompoundVersionDetails atTarget = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v71570.get(), "build", 1);
        CompoundVersionDetails belowTarget = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v71500.get(), "build", 1);

        // node already at v71570 upgrading to v71570 (same version restart / re-deploy) -> run
        assertTrue(converter.isInterested(atTarget, atTarget));
        // node below target upgrading to target -> run
        assertTrue(converter.isInterested(belowTarget, atTarget));
        // node below target, target still below converter version -> skip
        assertFalse(converter.isInterested(belowTarget, belowTarget));
    }
}
