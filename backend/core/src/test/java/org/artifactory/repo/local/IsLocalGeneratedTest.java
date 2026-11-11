package org.artifactory.repo.local;

import jakarta.annotation.Nonnull;
import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class IsLocalGeneratedTest {

    @Test
    void excludePropertyValueFromMirroring() {
        IsLocalGenerated checker = new DummyIsLocalGenerated();
        Assertions.assertEquals(Set.of("value"),
                checker.excludePropertyValueFromMirroring("propertyKey", Set.of("value"), RepoType.Npm));
    }

    static class DummyIsLocalGenerated implements IsLocalGenerated {
        @Override
        public boolean isLocalGenerated(@Nonnull RepoType repoType, @Nonnull String repoKey, @Nonnull String path) {
            return false;
        }
    }

}