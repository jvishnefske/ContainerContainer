package org.artifactory.repo.service.flexible.validators;

import ch.qos.logback.classic.Level;
import org.apache.http.HttpStatus;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.repo.exception.FatalCopyMoveException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.service.flexible.MoveCopyItemInfo;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.artifactory.sapi.fs.VfsItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Volodymyr Shulga
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class BasicMoveCopyValidatorTest {

    private BasicMoveCopyValidator validator;

    @Mock
    private MoveCopyItemInfo mockItemInfo;
    @Mock
    private MoveMultiStatusHolder mockStatus;
    @Mock
    private MoveCopyContext mockContext;
    @Mock
    private RepoPath mockSourceRepoPath;
    @Mock
    private RepoPath mockTargetRepoPath;
    @Mock
    private VfsItem<ItemInfo> mockSourceItem;
    @Mock
    private VfsItem<ItemInfo> mockTargetItem;
    @Mock
    private RepoRepoPath<LocalRepo<?, ?>> mockTargetRrp;
    @Mock
    private LocalRepo mockLocalRepo;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        validator = new BasicMoveCopyValidator();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void validateReturnsTrueWhenAllConditionsAreMet() {
        when(mockItemInfo.getSourceRepoPath()).thenReturn(mockSourceRepoPath);
        when(mockItemInfo.getTargetRepoPath()).thenReturn(mockTargetRepoPath);
        when(mockItemInfo.getSourceItem()).thenReturn(mockSourceItem);
        when(mockItemInfo.getTargetRrp()).thenReturn(mockTargetRrp);
        when(mockTargetRrp.getRepo()).thenReturn(mockLocalRepo);
        when(mockLocalRepo.isCache()).thenReturn(false);
        when(mockContext.isCopy()).thenReturn(false);
        when(mockContext.isCopyContent()).thenReturn(false);
        when(mockTargetItem.isFile()).thenReturn(false);
        when(mockSourceItem.isFolder()).thenReturn(false);

        boolean result = validator.validate(mockItemInfo, mockStatus, mockContext);

        assertTrue(result);
    }

    @Test
    void validateStatusWarnWhenSourceItemIsNull() {
        when(mockItemInfo.getSourceRepoPath()).thenReturn(mockSourceRepoPath);
        when(mockItemInfo.getSourceItem()).thenReturn(null);

        boolean validated = validator.validate(mockItemInfo, mockStatus, mockContext);

        assertFalse(validated);
        verify(mockStatus).warn(eq("Could not find item at mockSourceRepoPath"),
                eq(HttpStatus.SC_BAD_REQUEST), any(Logger.class));
    }

    @Test
    void validateReturnsFalseWhenSourceAndTargetAreSame() {
        when(mockItemInfo.getSourceRepoPath()).thenReturn(mockSourceRepoPath);
        when(mockItemInfo.getTargetRepoPath()).thenReturn(mockSourceRepoPath);
        when(mockItemInfo.getSourceItem()).thenReturn(mockSourceItem);
        when(mockContext.isCopy()).thenReturn(false);

        boolean result = validator.validate(mockItemInfo, mockStatus, mockContext);

        assertFalse(result);
        verify(mockStatus).error(anyString(), any(Logger.class));
    }

    @Test
    void validateThrowsExceptionWhenTargetIsCache() {
        when(mockItemInfo.getSourceRepoPath()).thenReturn(mockSourceRepoPath);
        when(mockItemInfo.getTargetRepoPath()).thenReturn(mockTargetRepoPath);
        when(mockItemInfo.getSourceItem()).thenReturn(mockSourceItem);
        when(mockItemInfo.getTargetRrp()).thenReturn(mockTargetRrp);
        when(mockTargetRrp.getRepo()).thenReturn(mockLocalRepo);
        when(mockLocalRepo.isCache()).thenReturn(true);
        when(mockContext.isCopy()).thenReturn(false);
        when(mockContext.isCopyContent()).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                validator.validate(mockItemInfo, mockStatus, mockContext));

        assertEquals("Target repository " + mockTargetRepoPath.getRepoKey() +
                " is a cache repository. move to cache repositories is not allowed.", exception.getMessage());
    }

    @Test
    void validateThrowsExceptionWhenMovingFolderToFile() {
        when(mockItemInfo.getSourceRepoPath()).thenReturn(mockSourceRepoPath);
        when(mockItemInfo.getTargetRepoPath()).thenReturn(mockTargetRepoPath);
        when(mockItemInfo.getSourceItem()).thenReturn(mockSourceItem);
        when(mockItemInfo.getTargetItem()).thenReturn(mockTargetItem);
        when(mockItemInfo.getTargetRrp()).thenReturn(mockTargetRrp);
        when(mockTargetItem.isFile()).thenReturn(true);
        when(mockTargetRrp.getRepo()).thenReturn(mockLocalRepo);
        when(mockSourceItem.isFolder()).thenReturn(true);
        when(mockContext.isCopy()).thenReturn(false);

        FatalCopyMoveException exception = assertThrows(FatalCopyMoveException.class, () ->
                validator.validate(mockItemInfo, mockStatus, mockContext));

        assertEquals("Can't move file to existing folder '" + mockTargetRepoPath + "'.", exception.getMessage());
        verify(mockStatus).errorWithDifferentLogLevel(anyString(), eq(HttpStatus.SC_BAD_REQUEST), eq(null),
                any(Logger.class), eq(Level.WARN));
    }

    @ParameterizedTest
    @MethodSource("provideMoveCopyItemInfo")
    void isInterestedReturnsTrueForAllInputs(MoveCopyItemInfo itemInfo, MoveCopyContext context) {
        assertTrue(validator.isInterested(itemInfo, context));
    }

    static Stream<Arguments> provideMoveCopyItemInfo() {
        return Stream.of(
                Arguments.of(mock(MoveCopyItemInfo.class), mock(MoveCopyContext.class)),
                Arguments.of(null, mock(MoveCopyContext.class)),
                Arguments.of(mock(MoveCopyItemInfo.class), null),
                Arguments.of(null, null)
        );
    }
}