package org.artifactory.ui.rest.resource.admin.advanced.sumologic;

import jakarta.ws.rs.core.Response;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.logging.sumologic.SumoLogicService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.sumologicVerifyDomain;
import static org.artifactory.ui.rest.resource.admin.advanced.sumologic.SumoLogicResource.ERROR_SUMO_LOGIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SumoLogicResource
 *
 * @author Eli Fishman
 */
@ExtendWith(MockitoExtension.class)
class SumoLogicResourceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private SumoLogicService sumoLogicService;

    @Mock
    private CentralConfigService centralConfigService;

    @Mock
    private CentralConfigDescriptor centralConfigDescriptor;

    @Mock
    private SumoLogicConfigDescriptor sumoLogicConfigDescriptor;

    private SumoLogicResource sumoLogicResource;

    @BeforeEach
    void setUp() {
        sumoLogicResource = new SumoLogicResource();
        injectMocks();
    }

    private void injectMocks() {
        try {
            Field sumoLogicServiceField =
                    SumoLogicResource.class.getDeclaredField("sumoLogicService");
            sumoLogicServiceField.setAccessible(true);
            sumoLogicServiceField.set(sumoLogicResource, sumoLogicService);

            Field centralConfigServiceField =
                    SumoLogicResource.class.getDeclaredField("centralConfigService");
            centralConfigServiceField.setAccessible(true);
            centralConfigServiceField.set(sumoLogicResource, centralConfigService);

        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocks", e);
        }
    }

    /**
     * Test data provider for invalid base_uri scenarios only
     */
    static Stream<Arguments> invalidBaseUris() {
        return Stream.of(
            Arguments.of("https://evil.com", "Malicious domain"),
            Arguments.of("https://sumologic.evil.com", "Subdomain of malicious domain"),
            Arguments.of("https://fake-sumologic.com", "Similar but fake domain"),
            Arguments.of("https://sumologic.com.evil.com", "Subdomain attack"),
            Arguments.of("http://sumologic.com", "HTTP instead of HTTPS"),
            Arguments.of("ftp://sumologic.com", "Wrong protocol"),
            Arguments.of("", "Empty string"),
            Arguments.of("not-a-url", "Invalid URL format"),
            Arguments.of("https://", "URL without host"),
            Arguments.of(null, "Null baseUri")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidBaseUris")
    void testCallbackFromSumoLogicAuthInvalidBaseUri(String baseUri, String testDescription) {
        String username = "testuser";
        String setup = "new_app";
        String code = "test-code";

        getOrCreateArtifactoryHomeStub().setProperty(sumologicVerifyDomain, String.valueOf(true));
        
        Response response = sumoLogicResource.callbackFromSumoLogicAuth(
                username, setup, code, baseUri, null, null);

        assertNotNull(response, "Response should not be null for test: " + testDescription);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus(),
                "Should return 400 Bad Request for invalid baseUri: " + testDescription);

        String responseEntity = (String) response.getEntity();
        assertNotNull(responseEntity, "Error response should have entity");

        if (baseUri == null || baseUri.isEmpty()) {
            assertEquals(ERROR_SUMO_LOGIC + "base_uri is required",
                    responseEntity,
                    "Should return 'base_uri is required' for empty/null baseUri");
        } else {
            assertEquals(ERROR_SUMO_LOGIC +
                            "base_uri must be of sumologic domain",
                    responseEntity,
                    "Should return domain validation error for invalid baseUri: " + testDescription);
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("validBaseUriScenarios")
    void testCallbackFromSumoLogicAuthValidBaseUri(String baseUri, String testDescription) {
        String username = "testuser";
        String setup = "new_app";
        String code = "test-code";
        
        when(sumoLogicService.createToken(anyString(), anyString(), anyString()))
                .thenReturn("mock-access-token");
        doNothing().when(sumoLogicService).setupApplication(anyString(), anyString(), anyBoolean());
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.getSumoLogicConfig()).thenReturn(sumoLogicConfigDescriptor);
        when(sumoLogicConfigDescriptor.getDashboardUrl()).thenReturn("https://dashboard.sumologic.com");

        getOrCreateArtifactoryHomeStub().setProperty(sumologicVerifyDomain, String.valueOf(true));
        
        Response response = sumoLogicResource.callbackFromSumoLogicAuth(
                username, setup, code, baseUri, null, null);

        assertNotNull(response, "Response should not be null for test: " + testDescription);
        assertEquals(Response.Status.SEE_OTHER.getStatusCode(), response.getStatus(),
                "Should return 303 See Other for valid baseUri: " + testDescription);
    }

    /**
     * Test data provider for valid base_uri scenarios only
     */
    static Stream<Arguments> validBaseUriScenarios() {
        return Stream.of(
            Arguments.of("https://sumologic.com", "Valid SumoLogic main domain"),
            Arguments.of("https://www.sumologic.com", "Valid SumoLogic www subdomain"),
            Arguments.of("https://api.sumologic.com", "Valid SumoLogic API subdomain"),
            Arguments.of("https://collectors.sumologic.com", "Valid SumoLogic collectors subdomain"),
            Arguments.of("https://SUMOlogic.COM", "Valid SumoLogic domain with mixed case")
        );
    }

    @Test
    void testCallbackFromSumoLogicAuthDomainVerificationDisabled() {
        String username = "testuser";
        String setup = "new_app";
        String code = "test-code";
        String baseUri = "https://evil.com"; // Invalid domain
        
        when(sumoLogicService.createToken(anyString(), anyString(), anyString()))
                .thenReturn("mock-access-token");
        doNothing().when(sumoLogicService).setupApplication(anyString(), anyString(), anyBoolean());
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.getSumoLogicConfig()).thenReturn(sumoLogicConfigDescriptor);
        when(sumoLogicConfigDescriptor.getDashboardUrl()).thenReturn("https://dashboard.sumologic.com");

        getOrCreateArtifactoryHomeStub().setProperty(sumologicVerifyDomain, String.valueOf(false));
        Response response = sumoLogicResource.callbackFromSumoLogicAuth(
                username, setup, code, baseUri, null, null);

        assertNotNull(response, "Response should not be null");
        assertEquals(Response.Status.SEE_OTHER.getStatusCode(), response.getStatus(),
                "Should return 303 See Other when domain verification is disabled, even with invalid domain");

    }

    @Test
    void testCallbackFromSumoLogicAuthMissingRequiredParameters() {
        Response response1 = sumoLogicResource.callbackFromSumoLogicAuth(
                "", "new_app", "test-code", "https://sumologic.com", null, null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
        assertEquals(ERROR_SUMO_LOGIC + "username is required",
                response1.getEntity());

        Response response2 = sumoLogicResource.callbackFromSumoLogicAuth(
                "testuser", "invalid_setup", "test-code", "https://sumologic.com", null, null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response2.getStatus());
        assertEquals(ERROR_SUMO_LOGIC +
                        "unexpected setup type: invalid_setup",
                response2.getEntity());

        Response response3 = sumoLogicResource.callbackFromSumoLogicAuth(
                "testuser", "new_app", "", "https://sumologic.com", null, null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response3.getStatus());
        assertEquals(ERROR_SUMO_LOGIC + "code is required",
                response3.getEntity());
    }
}
