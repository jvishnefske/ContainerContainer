package org.artifactory.repo.http;

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuavaCacheConnectionManagersHolderTest {

    @Test
    void testExplicitAddRemove() {
        ConnectionManagersHolder holder = new GuaveCacheConnectionManagersHolder();
        holder.put("a", new PoolingHttpClientConnectionManager());
        holder.put("b", new PoolingHttpClientConnectionManager());
        holder.put("c", new PoolingHttpClientConnectionManager());
        assertEquals(3, holder.size());

        holder.remove("a");
        holder.remove("b");
        assertEquals(1, holder.size());
    }
}