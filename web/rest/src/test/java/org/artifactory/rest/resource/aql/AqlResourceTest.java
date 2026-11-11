package org.artifactory.rest.resource.aql;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.AqlService;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.aql.AqlTooManyRequestsException;
import org.artifactory.aql.result.AqlRestResult;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLTimeoutException;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.aqlSearchQuerySizeLimit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class AqlResourceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AqlService aqlService;
    @Mock
    private SecurityService securityService;
    private AutoCloseable autoCloseable;

    @BeforeAll
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        when(securityService.isAnonymous()).thenReturn(false);
    }

    @AfterEach
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @ParameterizedTest
    @MethodSource("provideExceptions")
    public void testExecuteQueryTimeout(AqlException e) {
        AqlResource aqlResource = new AqlResource();
        ReflectionTestUtils.setField(aqlResource, "securityService", securityService);
        ReflectionTestUtils.setField(aqlResource, "aqlService", aqlService);
        when(aqlService.executeQueryLazyWithLimit(anyString())).thenThrow(e);

        assertThrows(AqlException.class, () -> aqlResource.execute(false, "something"));
    }

    @Test
    public void testAqlQuerySizeLimit() {
        AqlResource aqlResource = new AqlResource();
        ReflectionTestUtils.setField(aqlResource, "securityService", securityService);
        getOrCreateArtifactoryHomeStub().setProperty(aqlSearchQuerySizeLimit, String.valueOf(3));
        String query = "test";
        try (Response response = aqlResource.execute(false, query)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals(1, errorResponse.getErrors().size());
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), errorResponse.getErrors().get(0).getStatus());
            assertEquals(String.format(
                            "AQL query is too long; please reduce the query length to less than %s chars",
                            aqlSearchQuerySizeLimit.getInt()),
                    errorResponse.getErrors().get(0).getMessage());
        } catch (Exception ex) {
            fail();
        }
    }

    @Test
    public void execute_computesQueryTemplate_on429() {
        AqlResource aqlResource = new AqlResource();
        ReflectionTestUtils.setField(aqlResource, "securityService", securityService);
        ReflectionTestUtils.setField(aqlResource, "aqlService", aqlService);
        when(aqlService.executeQueryLazyWithLimit(anyString())).thenThrow(new AqlTooManyRequestsException());

        aqlResource.execute(false, "items.find()");

        verify(aqlService).computeQueryTemplate("items.find()");
    }

    @Test
    public void execute_doesNotRecordStats_on429() {
        AqlResource aqlResource = new AqlResource();
        ReflectionTestUtils.setField(aqlResource, "securityService", securityService);
        ReflectionTestUtils.setField(aqlResource, "aqlService", aqlService);
        when(aqlService.executeQueryLazyWithLimit(anyString())).thenThrow(new AqlTooManyRequestsException());

        aqlResource.execute(false, "items.find()");

        verify(aqlService, never()).streamResponse(anyBoolean(), any(), any());
    }

    @Test
    public void execute_doesNotCallComputeQueryTemplate_onSuccessPath() throws Exception {
        AqlResource aqlResource = new AqlResource();
        ReflectionTestUtils.setField(aqlResource, "securityService", securityService);
        ReflectionTestUtils.setField(aqlResource, "aqlService", aqlService);
        when(aqlService.executeQueryLazyWithLimit(anyString())).thenReturn(mock(AqlRestResult.class));
        when(aqlService.streamResponse(anyBoolean(), any(), any())).thenReturn(Response.ok().build());

        aqlResource.execute(false, "items.find()");

        verify(aqlService, never()).computeQueryTemplate(anyString());
    }

    private static Stream<Arguments> provideExceptions() {
        return Stream.of(
                Arguments.of(new AqlException("Failed to execute the following sql query: something",
                        new SQLTimeoutException("The statement has been cancelled or timed out."))),// derby
                Arguments.of(new AqlException("Failed to execute the following sql query: something",
                        new Exception("ERROR: canceling statement due to user request"))), //psql
                Arguments.of(new AqlException("Failed to execute the following sql query: something",
                        new SQLTimeoutException("The query has timed out."))), //mssql
                Arguments.of(new AqlException("Failed to execute the following sql query: something",
                        new SQLTimeoutException("Statement cancelled due to timeout or client request"))) //mysql
        );
    }
}