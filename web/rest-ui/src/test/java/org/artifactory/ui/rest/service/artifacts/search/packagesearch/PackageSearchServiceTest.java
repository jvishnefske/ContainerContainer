package org.artifactory.ui.rest.service.artifacts.search.packagesearch;

import jakarta.ws.rs.InternalServerErrorException;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Katrin Shor
 */
@ExtendWith(MockitoExtension.class)
class PackageSearchServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AqlService aqlService;
    @Mock
    private AqlBase<? extends AqlBase, AqlBaseFullRowImpl> query;
    @Mock
    private AqlLazyResult<AqlBaseFullRowImpl> aqlLazyResult;
    @InjectMocks
    private PackageSearchService packageSearchService;

    private static final Long MOCK_TIMEOUT_VALUE = 300L;

    @BeforeEach
    void setUp() {
        homeStub.setProperty(ConstantValues.aqlQueryTimeoutForUi.getPropertyName(), String.valueOf(MOCK_TIMEOUT_VALUE));
    }

    @Test
    void testExecuteSearchThrowsAqlExceptionFromExecuteQueryLazy() {
        final AqlException aqlException = new AqlException("Failed to execute AQL query");
        when(aqlService.executeQueryLazy(query, MOCK_TIMEOUT_VALUE)).thenThrow(aqlException);

        final AqlException thrown = assertThrows(AqlException.class, () -> packageSearchService.executeSearch(query));

        assertEquals(aqlException, thrown);
    }

    @Test
    void testExecuteSearchThrowsAqlExceptionFromAsStream() {
        final AqlException aqlException = new AqlException("Failed to execute AQL query");
        when(aqlService.executeQueryLazy(query, MOCK_TIMEOUT_VALUE)).thenReturn(aqlLazyResult);
        when(aqlLazyResult.asStream(any(Consumer.class))).thenThrow(aqlException);

        final AqlException thrown = assertThrows(AqlException.class,
                () -> packageSearchService.executeSearch(query));

        assertEquals("Failed to execute AQL query", thrown.getMessage());
    }

    @Test
    void testExecuteSearchWrapsNonAqlExceptionInInternalServerErrorException() {
        when(aqlService.executeQueryLazy(query, MOCK_TIMEOUT_VALUE)).thenReturn(aqlLazyResult);
        when(aqlLazyResult.asStream(any(Consumer.class))).thenThrow(new RuntimeException("Database connection error"));

        final InternalServerErrorException thrown = assertThrows(InternalServerErrorException.class,
                () -> packageSearchService.executeSearch(query));

        assertEquals("Error executing search AQL query ", thrown.getMessage());
    }
}

