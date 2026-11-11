package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.recalculateindex;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.cocoapods.CocoaPodsAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.repo.PackageType;
import org.artifactory.ph.PackageResourceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * @author Priyanshu Gupta
 */
class PodsIndexCalculatorTest {
    private AutoCloseable autoCloseable;

    private final String repoKey = "repo";

    @Mock
    private ConfigurableEnvironment env;

    private ArtifactoryContext context;

    private AddonsManager addonsManager;

    private CocoaPodsAddon cocoaPodsAddon;

    @BeforeEach
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        addonsManager = mock(AddonsManager.class);
        cocoaPodsAddon = mock(CocoaPodsAddon.class);
    }

    @AfterEach
    public void cleanup() throws Exception {
        autoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testCalculateIndexForLegacyProfile() throws Exception {
        when(env.getActiveProfiles()).thenReturn(new String[] {});
        when(addonsManager.addonByType(CocoaPodsAddon.class)).thenReturn(cocoaPodsAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.beanForType(ConfigurableEnvironment.class)).thenReturn(env);

        PodsIndexCalculator indexCalculator = new PodsIndexCalculator();
        indexCalculator.setRepoKey(repoKey);
        indexCalculator.calculateIndex();
        verify(cocoaPodsAddon, times(1)).reindexAsync(repoKey);
    }

    @Test
    void testCalculateIndexForNonLegacyProfile() throws Exception {
        when(env.getActiveProfiles()).thenReturn(new String[] {"cocoapodsNew"});
        PackageResourceService packageResourceService = mock(PackageResourceService.class);
        when(context.beanForType(PackageResourceService.class)).thenReturn(packageResourceService);
        when(addonsManager.addonByType(CocoaPodsAddon.class)).thenReturn(cocoaPodsAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.beanForType(ConfigurableEnvironment.class)).thenReturn(env);

        PodsIndexCalculator indexCalculator = new PodsIndexCalculator();
        indexCalculator.setRepoKey(repoKey);
        indexCalculator.calculateIndex();
        verify(cocoaPodsAddon, times(1)).reindexAsync(repoKey);
        Mockito.verify(packageResourceService).reindex(PackageType.COCOAPODS, repoKey, null);
    }
}
