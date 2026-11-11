package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;


import org.artifactory.addon.AddonsManager;
import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD;

/**
 * @author Package Generator
 */
class NixTypeSpecificConfigModelTest {

    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.Nix, new NixTypeSpecificConfigModel().getRepoType());
    }

    @Test
    void toStringTest() {
        String actual = new NixTypeSpecificConfigModel().toString();
        Assertions.assertEquals(
                "{\"repoType\":\"Nix\",\"url\":\"https://cache.nixos.org\"," +
                        "\"virtualRetrievalCachePeriodSecs\":7200,\"repoType\":\"Nix\"," +
                        "\"enableTokenAuthentication\":false}", actual);
    }

    @Test
    void validateVirtualTypeSpecific_whenCachePeriodIsNull_setsDefaultValue() {
        // Given
        NixTypeSpecificConfigModel model = new NixTypeSpecificConfigModel();
        model.setVirtualRetrievalCachePeriodSecs(null);
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);

        // When
        model.validateVirtualTypeSpecific(addonsManager);

        // Then
        Assertions.assertEquals(DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD,
                model.getVirtualRetrievalCachePeriodSecs());
    }

    @Test
    void validateVirtualTypeSpecific_whenCachePeriodHasValue_keepsValue() {
        // Given
        Long customCachePeriod = 3600L;
        NixTypeSpecificConfigModel model = new NixTypeSpecificConfigModel();
        model.setVirtualRetrievalCachePeriodSecs(customCachePeriod);
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);

        // When
        model.validateVirtualTypeSpecific(addonsManager);

        // Then
        Assertions.assertEquals(customCachePeriod, model.getVirtualRetrievalCachePeriodSecs());
    }
}