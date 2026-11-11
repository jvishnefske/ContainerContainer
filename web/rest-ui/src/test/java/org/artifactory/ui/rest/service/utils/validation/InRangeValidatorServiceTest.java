package org.artifactory.ui.rest.service.utils.validation;

import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Hussein Alshqerat
 */
class InRangeValidatorServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private final InRangeValidatorService inRangeValidatorService = new InRangeValidatorService();

    private static Stream<Object[]> invalidKeyProvider() {
        return Stream.of(
                new Object[] {"bad-key", "500", "{\"error\":\"The key: bad-key dose not exist.\"}"},
                new Object[] {"uiItemsBrowserPagingMaxLimit", "-1",
                        "{\"error\":\"The value must fall within " + "the range of 1 to 100000.\"}"},
                new Object[] {"uiItemsBrowserPagingMaxLimit", "cool",
                        "{\"error\":\"Value must be a numerical value in the rage of 1 - 100000.\"}"});
    }

    @ParameterizedTest
    @MethodSource("invalidKeyProvider")
    public void testInvalidKey(String key, String value, String expectedError) {
        ArtifactoryRestRequest<Void> request = mock(ArtifactoryRestRequest.class);
        RestResponse<?> response = new ArtifactoryRestResponse<>();
        when(request.getQueryParamByKey("key")).thenReturn(key);
        when(request.getQueryParamByKey("value")).thenReturn(value);
        inRangeValidatorService.execute(request, response);
        assertEquals(expectedError, response.getEntity());
    }

    @Test
    public void testValidValue() {
        ArtifactoryRestRequest<Void> request = mock(ArtifactoryRestRequest.class);
        RestResponse<?> response = new ArtifactoryRestResponse<>();
        when(request.getQueryParamByKey("key")).thenReturn("uiItemsBrowserPagingMaxLimit");
        when(request.getQueryParamByKey("value")).thenReturn("100");
        inRangeValidatorService.execute(request, response);
        assertEquals("{\"info\":\"Value validated.\"}", response.getEntity());
    }
}