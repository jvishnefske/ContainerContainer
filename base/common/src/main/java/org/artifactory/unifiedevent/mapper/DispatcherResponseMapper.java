package org.artifactory.unifiedevent.mapper;

import lombok.experimental.UtilityClass;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.ResponseContextData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class DispatcherResponseMapper {

    public DispatcherResponse toConsolidatedDispatcherResponse(List<DispatcherResponse> responses) {
        // How to consolidate the context variables
        if (responses == null) {
            return DispatcherResponse.builder().shouldProceed(true).build();
        }
        Map<String, String> combinedHeaders = new HashMap<>();
        ResponseContextData.ResponseContextDataBuilder combinedData = ResponseContextData.builder();
        for (DispatcherResponse response : responses) {
            if (response == null) {
                continue;
            }
            if (!response.shouldProceed()) {
                return DispatcherResponse.builder().shouldProceed(false).message(response.message()).build();
            }
            combineResponseData(response, combinedHeaders, combinedData);
        }
        return DispatcherResponse.builder()
                .shouldProceed(true)
                .data(combinedData.headers(combinedHeaders).build())
                .build();
    }

    void combineResponseData(DispatcherResponse response, Map<String, String> combinedHeaders,
            ResponseContextData.ResponseContextDataBuilder dataBuilder) {
        if (response.data() == null) {
            return;
        }
        if (response.data().headers() != null) {
            combinedHeaders.putAll(response.data().headers());
        }
        if (response.data().statusCode() != null) {
            dataBuilder.statusCode(response.data().statusCode());
        }
        if (response.data().message() != null) {
            dataBuilder.message(response.data().message());
        }
        if (response.data().size() != null) {
            dataBuilder.size(response.data().size());
        }
        if (response.data().modifiedRepoPath() != null) {
            dataBuilder.modifiedRepoPath(response.data().modifiedRepoPath());
        }
    }
}
