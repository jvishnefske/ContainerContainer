package org.artifactory.search;

import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.repo.service.RepositoryServiceImpl;
import org.artifactory.storage.spring.ArtifactoryStorageContext;
import org.artifactory.storage.spring.StorageContextHelper;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.artifactory.common.ConstantValues.searchLimitAnonymousUserOnly;
import static org.artifactory.common.ConstantValues.searchUserQueryLimit;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VisibleAqlItemsSearchHelperTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private SecurityService securityService;

    private VisibleAqlItemsSearchHelper visibleAqlItemsSearchHelper;

    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        getOrCreateArtifactoryHomeStub().setProperty(searchUserQueryLimit, String.valueOf(10));
        visibleAqlItemsSearchHelper = new VisibleAqlItemsSearchHelper(securityService, new RepositoryServiceImpl());
    }

    @AfterEach
    public void unbind() throws Exception {
        mocks.close();
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void searchVisibleArtifactsAsStreamTest(boolean isSearchLimitAnonymousUserOnly) {
        getOrCreateArtifactoryHomeStub().setProperty(searchLimitAnonymousUserOnly,
                String.valueOf(isSearchLimitAnonymousUserOnly));
        if (isSearchLimitAnonymousUserOnly) {
            Mockito.when(securityService.isAnonymous()).thenReturn(true);
        } else {
            Mockito.when(securityService.isAdmin()).thenReturn(false);
        }

        try (MockedStatic<StorageContextHelper> storageContextHelper = mockStatic(StorageContextHelper.class)) {
            AqlBase.AndClause<AqlApiItem> query = new AqlBase.AndClause<>();
            ArtifactoryStorageContext artifactoryStorageContext = mock(ArtifactoryStorageContext.class);
            AqlService aqlService = mock(AqlService.class);
            when(artifactoryStorageContext.beanForType(AqlService.class)).thenReturn(aqlService);
            storageContextHelper.when(StorageContextHelper::get).thenReturn(artifactoryStorageContext);
            var aqlLazyResult = mock(AqlLazyResult.class);
            when(aqlService.executeQueryLazy(any(AqlApiItem.class))).thenReturn(aqlLazyResult);

            visibleAqlItemsSearchHelper.searchVisibleArtifactsAsStream(query, 100, true, null);

            verify(aqlService, times(1)).executeQueryLazy(any(AqlApiItem.class));
        }
    }
}
