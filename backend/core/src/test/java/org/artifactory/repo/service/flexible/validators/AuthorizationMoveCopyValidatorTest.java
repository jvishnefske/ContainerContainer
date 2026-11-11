package org.artifactory.repo.service.flexible.validators;

import org.apache.http.HttpStatus;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.flexible.MoveCopyItemInfo;
import org.artifactory.sapi.fs.VfsItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Asaf Zalcman
 */
class AuthorizationMoveCopyValidatorTest {

    @Mock
    AuthorizationService authorizationService;
    @Mock
    MoveCopyItemInfo moveCopyItemInfo;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
        verifyNoMoreInteractions(authorizationService, moveCopyItemInfo);
        mocks.close();
    }

    @Test
    void testValidateWithoutReadPermissionToSourceRepo() {
        RepoPath sourceRepoPath = Mockito.mock(RepoPath.class);
        when(moveCopyItemInfo.getSourceRepoPath()).thenReturn(sourceRepoPath);
        when(authorizationService.canRead(sourceRepoPath)).thenReturn(false);
        MoveMultiStatusHolder moveMultiStatusHolder = new MoveMultiStatusHolder();
        Assertions.assertFalse(
                new AuthorizationMoveCopyValidator(authorizationService)
                        .validate(moveCopyItemInfo, moveMultiStatusHolder, null));
        Assertions.assertEquals(HttpStatus.SC_FORBIDDEN, moveMultiStatusHolder.getStatusCode());
        Assertions.assertTrue(moveMultiStatusHolder.getStatusMsg().contains("User doesn't have permissions to read"));
        verify(authorizationService, times(1)).canRead(sourceRepoPath);
        verify(moveCopyItemInfo, times(1)).getSourceRepoPath();
    }

    @Test
    void testValidateWithoutDeletePermissionToTargetRepo() {
        RepoPath targetRepoPath = Mockito.mock(RepoPath.class);

        when(moveCopyItemInfo.getSourceRepoPath()).thenReturn(Mockito.mock(RepoPath.class));
        when(moveCopyItemInfo.getTargetRepoPath()).thenReturn(targetRepoPath);
        when(moveCopyItemInfo.getTargetItem()).thenReturn(Mockito.mock(VfsItem.class));
        when(authorizationService.canRead(any())).thenReturn(true);
        when(authorizationService.canDelete(targetRepoPath)).thenReturn(false);

        MoveMultiStatusHolder moveMultiStatusHolder = new MoveMultiStatusHolder();
        Assertions.assertFalse(
                new AuthorizationMoveCopyValidator(authorizationService)
                        .validate(moveCopyItemInfo, moveMultiStatusHolder, null));
        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, moveMultiStatusHolder.getStatusCode());
        Assertions
                .assertTrue(moveMultiStatusHolder.getStatusMsg().contains("User doesn't have permissions to override"));
        verify(authorizationService, times(1)).canRead(any());
        verify(authorizationService, times(1)).canDelete(targetRepoPath);
        verify(moveCopyItemInfo, times(1)).getSourceRepoPath();
        verify(moveCopyItemInfo, times(1)).getTargetRepoPath();
        verify(moveCopyItemInfo, times(1)).getTargetItem();
    }

    @Test
    void testValidateWithoutDeployPermissionToTargetRepo() {
        RepoPath targetRepoPath = Mockito.mock(RepoPath.class);

        when(moveCopyItemInfo.getSourceRepoPath()).thenReturn(Mockito.mock(RepoPath.class));
        when(moveCopyItemInfo.getTargetRepoPath()).thenReturn(targetRepoPath);
        when(moveCopyItemInfo.getTargetItem()).thenReturn(null);
        when(authorizationService.canRead(any())).thenReturn(true);
        when(authorizationService.canDeploy(targetRepoPath)).thenReturn(false);

        MoveMultiStatusHolder moveMultiStatusHolder = new MoveMultiStatusHolder();
        Assertions.assertFalse(
                new AuthorizationMoveCopyValidator(authorizationService)
                        .validate(moveCopyItemInfo, moveMultiStatusHolder, null));
        Assertions.assertEquals(HttpStatus.SC_FORBIDDEN, moveMultiStatusHolder.getStatusCode());
        Assertions
                .assertTrue(moveMultiStatusHolder.getStatusMsg().contains("User doesn't have permissions to create"));
        verify(authorizationService, times(1)).canRead(any());
        verify(authorizationService, times(1)).canDeploy(targetRepoPath);
        verify(moveCopyItemInfo, times(1)).getSourceRepoPath();
        verify(moveCopyItemInfo, times(1)).getTargetRepoPath();
        verify(moveCopyItemInfo, times(1)).getTargetItem();
    }

    @ParameterizedTest
    @MethodSource("testValidateProvider")
    void testValidate(boolean deletePermission, boolean deployPermission, VfsItem targetItem) {
        RepoPath targetRepoPath = Mockito.mock(RepoPath.class);

        when(moveCopyItemInfo.getSourceRepoPath()).thenReturn(Mockito.mock(RepoPath.class));
        when(moveCopyItemInfo.getTargetRepoPath()).thenReturn(targetRepoPath);
        when(moveCopyItemInfo.getTargetItem()).thenReturn(targetItem);
        when(authorizationService.canRead(any())).thenReturn(true);
        when(authorizationService.canDeploy(targetRepoPath)).thenReturn(deployPermission);
        when(authorizationService.canDelete(targetRepoPath)).thenReturn(deletePermission);

        MoveMultiStatusHolder moveMultiStatusHolder = spy(MoveMultiStatusHolder.class);
        Assertions.assertTrue(
                new AuthorizationMoveCopyValidator(authorizationService)
                        .validate(moveCopyItemInfo, moveMultiStatusHolder, null));
        verify(authorizationService, times(1)).canRead(any());
        verify(moveMultiStatusHolder, times(1)).setActivateLogging(true);
        verify(moveMultiStatusHolder, times(1)).setActivateLogging(false);
        verify(authorizationService, times(targetItem == null ? 1 : 0)).canDeploy(targetRepoPath);
        verify(authorizationService, times(targetItem == null ? 0 : 1)).canDelete(targetRepoPath);
        verify(moveCopyItemInfo, times(1)).getSourceRepoPath();
        verify(moveCopyItemInfo, times(1)).getTargetRepoPath();
        verify(moveCopyItemInfo, times(1)).getTargetItem();
    }

    public static Object[][] testValidateProvider() {
        return new Object[][] {
                // DeletePermission,DeployPermission TargetItem
                {true, false, Mockito.mock(VfsItem.class)},
                {false, true, null}
        };
    }
}