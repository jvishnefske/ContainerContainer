package org.artifactory.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


class MailUtilsTest {

    @ParameterizedTest
    @MethodSource("getMessageProvider")
    void getMessage(String serverUrlForMail, String expectedMessage) {
        String res = MailUtils.getMessage(serverUrlForMail).toString();
        Assertions.assertEquals(expectedMessage, res);
    }

    @ParameterizedTest
    @MethodSource("getMessageProvider")
    void getTestMessage(String serverUrlForMail, String expectedMessageSuffix) {
        String expectedMessage = "This is a test message from Artifactory<br/>" + expectedMessageSuffix;
        String res = MailUtils.getTestMessage(serverUrlForMail).toString();
        Assertions.assertEquals(expectedMessage, res);
    }

    public static Object[][] getMessageProvider() {
        return new Object[][] {
                // serverUrlForMail,expectedMessage
                {"", "No Artifactory base URL is configured"},
                {"localhost", "Your Artifactory base URL is: <a href=localhost target=\"blank\">localhost</a>"}
        };
    }
}