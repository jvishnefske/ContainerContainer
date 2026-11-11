package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.service.PackageSpecificationAdapter;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackageMetadataNonTrashableItemTest {
    private static final String PATH = "path";
    private static final String REPO = "repo";

    @Mock
    PackageSpecificationAdapter packageSpecificationAdapter;
    @Mock
    LocalConfig localConfig;
    PackageMetadataNonTrashableItem packageMetadataNonTrashableItem;
    AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        packageMetadataNonTrashableItem = new PackageMetadataNonTrashableItem(packageSpecificationAdapter);
        when(localConfig.getKey()).thenReturn(REPO);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
        Mockito.verifyNoMoreInteractions(packageSpecificationAdapter);
    }

    @Test
    void skipTrashReturnFalseInException() throws PackageException {
        when(localConfig.getPackageType()).thenReturn(RepoType.Npm);
        when(packageSpecificationAdapter.isExpirable(REPO, PackageType.NPM, PATH)).thenThrow(
                new PackageException(400, "", ""));
        assertFalse(packageMetadataNonTrashableItem.skipTrash(localConfig, PATH));
        verify(packageSpecificationAdapter, times(1)).isExpirable(any(), any(), any());
    }

    @Test
    void skipTrashWithoutSupportedPackage() {
        when(localConfig.getPackageType()).thenReturn(RepoType.Distribution);
        assertFalse(packageMetadataNonTrashableItem.skipTrash(localConfig, PATH));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void skipTrash(boolean isExpirable) throws PackageException {
        when(localConfig.getPackageType()).thenReturn(RepoType.Npm);
        when(packageSpecificationAdapter.isExpirable(REPO, PackageType.NPM, PATH)).thenReturn(isExpirable);
        assertEquals(isExpirable, packageMetadataNonTrashableItem.skipTrash(localConfig, PATH));
        verify(packageSpecificationAdapter, times(1)).isExpirable(any(), any(), any());
    }
}