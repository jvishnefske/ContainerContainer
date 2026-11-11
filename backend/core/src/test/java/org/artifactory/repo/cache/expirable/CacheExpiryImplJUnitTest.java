package org.artifactory.repo.cache.expirable;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class CacheExpiryImplJUnitTest {
    @Mock
    ArtifactoryApplicationContext context;
    CacheExpiryImpl cacheExpiry;
    CacheExpiryChecker mavenCheckerMock;
    CacheExpiryChecker genericCheckerMock;

    @BeforeEach
    void setup() {
        this.mavenCheckerMock = mock(CacheExpiryChecker.class);
        when(this.mavenCheckerMock.getTypes()).thenReturn(new RepoType[]{RepoType.Maven});
        Mockito.lenient()
                .when(this.mavenCheckerMock.getMetadataArtifactType(
                        eq(RepoType.Maven), anyString(), eq("maven-metadata.xml")))
                .thenReturn("repo_index");

        this.genericCheckerMock = mock(CacheExpiryChecker.class);
        when(this.genericCheckerMock.getTypes()).thenReturn(new RepoType[]{RepoType.Generic});

        when(context.beansForType(CacheExpiryChecker.class)).thenReturn(Map.of(
                "mavenCheckerMock", this.mavenCheckerMock,
                "genericCheckerMock", this.genericCheckerMock)
        );

        this.cacheExpiry = new CacheExpiryImpl();
        this.cacheExpiry.setApplicationContext(this.context);
        this.cacheExpiry.init();
    }

    @CsvSource({
            "Maven,libs-release,maven-metadata.xml,repo_index",
            "Maven,libs-release,commons/1.0/pom.xml,",
            // Generic does not hae any metadata requests
            "Generic,libs-release,maven-metadata.xml,",
            // NPM does not even have a checked (in this test suite)
            "Npm,npm-test,sample/1.0/package.json,",
    })
    @ParameterizedTest
    void getMetadataArtifactType(RepoType type, String repo, String path, String expectedType) {
        String actual = this.cacheExpiry.getMetadataArtifactType(type, repo, path);
        assertEquals(expectedType, actual);
    }
}
