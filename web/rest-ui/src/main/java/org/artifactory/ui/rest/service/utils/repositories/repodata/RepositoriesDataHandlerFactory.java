package org.artifactory.ui.rest.service.utils.repositories.repodata;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.ui.rest.service.utils.repositories.repodata.DefaultRequestHandler.NO_QUERY_PARAM;

@Slf4j
@Component
public class RepositoriesDataHandlerFactory<T> {

    private final Map<String, GetRepositoriesDataHandler<T>> handlerMap = new HashMap<>();

    @Autowired
    public RepositoriesDataHandlerFactory(List<GetRepositoriesDataHandler<T>> handlers) {
        if (handlers == null) {
            return;
        }
        for (GetRepositoriesDataHandler<T> handler : handlers) {
            handlerMap.put(handler.getHandledQueryParam(), handler);
        }
    }

    public GetRepositoriesDataHandler<T> getHandler(String queryParam) {
        return queryParam != null ? handlerMap.get(queryParam) : handlerMap.get(NO_QUERY_PARAM);
    }

    public Set<String> getHandledQueryParams() {
        return Set.copyOf(handlerMap.keySet());
    }
}