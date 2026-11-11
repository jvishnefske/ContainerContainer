package org.artifactory.repo.http;

import org.artifactory.repo.http.mbean.HTTPConnectionPool;
import org.springframework.stereotype.Service;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

@Service
public class HttpConnectionPoolMonitorService {

    private Map<String, WeakReference<HTTPConnectionPool>> poolMap = new HashMap<>();

    public void monitorHttpPool(String jmxName, HTTPConnectionPool httpConnectionPool) {
        this.poolMap.put(jmxName, new WeakReference<>(httpConnectionPool));
    }

    public Map<String, WeakReference<HTTPConnectionPool>> getConnectionPools() {
        return this.poolMap;
    }
}
