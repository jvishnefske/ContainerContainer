package org.artifactory.federation;

import org.artifactory.api.rest.restmodel.JsonUtil;
import org.artifactory.federated.service.RequestTunnelingService;
import org.artifactory.federated.exception.RequestTunnelingException;
import org.artifactory.federated.model.CrossMemberRequest;
import org.artifactory.federated.model.CrossMemberResponse;
import org.artifactory.federated.model.SingleMemberResponse;
import org.artifactory.federated.model.RequestTunnelingStatus;
import org.jfrog.client.http.RestResponseImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class RequestTunnelingServiceTest {
    @Test
    void testFederateRequestTunneling() throws RequestTunnelingException {
        RequestTunnelingService
                requestTunnelingService = Mockito.mock(RequestTunnelingService.class);

        CrossMemberRequest request =
                CrossMemberRequest.builder()
                        .repoKey("repoKey")
                        .refPath("repositories/" + RequestTunnelingService.REF_PATH_REPO_TEMPLATE)
                        .method(HttpMethod.POST)
                        .payload("{\"proxy\" : \"dummyProxy\"}")
                        .headers(Map.of("header1", "value1"))
                        .build();

        RestResponseImpl restResponse =
                new RestResponseImpl(200, "clientResponse".getBytes(), Charset.defaultCharset());
        CrossMemberResponse response = new CrossMemberResponse(200,
                List.of(new SingleMemberResponse("memberUrl", RequestTunnelingStatus.MEMBER_HEALTHY,
                        JsonUtil.jsonToString(restResponse))));
        when(requestTunnelingService.tunnelRequest(request)).thenReturn(response);

        CrossMemberResponse actualResponse =
                requestTunnelingService.tunnelRequest(request);

        assertEquals(1, actualResponse.memberResponses().size());
        assertEquals("memberUrl", actualResponse.memberResponses().get(0).memberUrl());
        assertEquals(RequestTunnelingStatus.MEMBER_HEALTHY, actualResponse.memberResponses().get(0).status());
    }

    @Test
    void testFederateRequestTunnelingThrowsException() throws RequestTunnelingException {
        RequestTunnelingService
                requestTunnelingService = Mockito.mock(RequestTunnelingService.class);

        CrossMemberRequest request =
                CrossMemberRequest.builder()
                        .repoKey("repoKey")
                        .refPath("repositories/" + RequestTunnelingService.REF_PATH_REPO_TEMPLATE)
                        .method(HttpMethod.POST)
                        .payload("{\"proxy\" : \"dummyProxy\"}")
                        .headers(Map.of("header1", "value1"))
                        .build();

        doThrow(new RequestTunnelingException("error")).when(
                requestTunnelingService).tunnelRequest(request);

        assertThrows(RequestTunnelingException.class,
                () -> requestTunnelingService.tunnelRequest(request));

        doThrow(new RequestTunnelingException("error", new Exception("error"))).when(
                requestTunnelingService).tunnelRequest(request);

        assertThrows(RequestTunnelingException.class,
                () -> requestTunnelingService.tunnelRequest(request));
    }
}