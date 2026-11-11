/*
 *
 * Copyright 2018 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.ui.rest.resource.jcr;

import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.HttpClientConfigurator;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.stubbing.Answer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * RTDEV-82727: verifies {@link JcrXrayService} passes {@link ConstantValues#xrayClientConnectionRequestTimeoutMillis}
 * into {@link HttpClientConfigurator#connectionRequestTimeout(int)}.
 */
class JcrXrayServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static Answer<Object> fluentSelfReturningAndBuildCloseableHttpClient() {
        return invocation -> {
            Class<?> retType = invocation.getMethod().getReturnType();
            if (CloseableHttpClient.class.isAssignableFrom(retType)) {
                return mock(CloseableHttpClient.class);
            }
            return invocation.getMock();
        };
    }

    @Test
    void createClientPassesConnectionRequestTimeoutFromConstantValues() {
        setStringSystemProperty(ConstantValues.xrayClientConnectionRequestTimeoutMillis, "48");
        try (MockedConstruction<HttpClientConfigurator> mocked = mockConstruction(
                HttpClientConfigurator.class,
                withSettings().defaultAnswer(fluentSelfReturningAndBuildCloseableHttpClient()),
                (mock, context) -> {})) {
            new JcrXrayService();
            verify(mocked.constructed().get(0)).connectionRequestTimeout(48);
        }
    }
}
