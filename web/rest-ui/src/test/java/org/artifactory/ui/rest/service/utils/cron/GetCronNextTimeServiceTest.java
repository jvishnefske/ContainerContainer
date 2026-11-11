package org.artifactory.ui.rest.service.utils.cron;

import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.artifactory.ui.rest.service.utils.cron.GetCronNextTimeService.EMPTY_CRON_ERROR_MESSAGE;
import static org.artifactory.ui.rest.service.utils.cron.GetCronNextTimeService.INVALID_CRON_ERROR_MESSAGE;
import static org.artifactory.ui.rest.service.utils.cron.GetCronNextTimeService.PAST_CRON_WARNING_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Hussein Alshqerat
 */
class GetCronNextTimeServiceTest {
    private static final String VALID_CRON_EXPRESSION = "0 0 0 ? * * *";
    private static final String INVALID_CRON_EXPRESSION = "0 0 0 19 19 ? ?";
    private static final String PAST_CRON_EXPRESSION = "0 0 0 19 08 ? 1998";
    private static final String EMPTY_CRON_EXPRESSION = "";
    private static final GetCronNextTimeService getCronNextTimeService = new GetCronNextTimeService();
    private ArtifactoryRestResponse<?> response;

    @BeforeEach
    void setup() {
        response = new ArtifactoryRestResponse<>();
    }

    @Test
    void testGetNextTimeForPastCronEXP() {
        String nextRunCronExp = getCronNextTimeService.getNextRunTime(PAST_CRON_EXPRESSION, response);
        assertNull(nextRunCronExp);
        assertEquals(PAST_CRON_WARNING_MESSAGE, response.getFeedbackMsg().getWarn());
    }

    @Test
    void testGetNextTimeForValidCronExp() {
        String nextRun = getCronNextTimeService.getNextRunTime(VALID_CRON_EXPRESSION, response);
        assertNotNull(nextRun);
    }

    @ParameterizedTest
    @MethodSource("invalidCronExpressions")
    void testGetNextTimeWithInvalidCronExp(String cronExpression, String expectedMessage) {
        String nextRun = getCronNextTimeService.getNextRunTime(cronExpression, response);
        assertNull(nextRun);
        assertEquals(expectedMessage, response.getFeedbackMsg().getError());
    }

    private static Stream<Arguments> invalidCronExpressions() {
        return Stream.of(
                Arguments.of(EMPTY_CRON_EXPRESSION, EMPTY_CRON_ERROR_MESSAGE),
                Arguments.of(INVALID_CRON_EXPRESSION, INVALID_CRON_ERROR_MESSAGE)
        );
    }
}