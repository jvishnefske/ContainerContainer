package org.artifactory.repo.http;

import org.artifactory.repo.http.mbean.HTTPConnectionPool;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class HttpConnectionPoolMonitorServiceTest {


    @Test
    public void testConnectionPool() {
        HTTPConnectionPool pool = mock(HTTPConnectionPool.class);
        HttpConnectionPoolMonitorService monitorService = new HttpConnectionPoolMonitorService();
        assertTrue(monitorService.getConnectionPools().isEmpty());
        monitorService.monitorHttpPool("pool1", pool);
        assertEquals(1, monitorService.getConnectionPools().size());
        assertEquals(pool, monitorService.getConnectionPools().get("pool1").get());
    }

    @Test
    public void testMultiConnectionPool() {
        HTTPConnectionPool pool = mock(HTTPConnectionPool.class);
        when(pool.getAvailable()).thenReturn(10);
        HTTPConnectionPool pool2 = mock(HTTPConnectionPool.class);
        when(pool2.getAvailable()).thenReturn(140);
        HttpConnectionPoolMonitorService monitorService = new HttpConnectionPoolMonitorService();
        assertTrue(monitorService.getConnectionPools().isEmpty());
        monitorService.monitorHttpPool("pool1", pool);
        monitorService.monitorHttpPool("pool2", pool2);
        assertEquals(2, monitorService.getConnectionPools().size());
        assertNotNull(monitorService.getConnectionPools().get("pool1"));
        HTTPConnectionPool pool1Object = monitorService.getConnectionPools().get("pool1").get();
        HTTPConnectionPool pool2Object = monitorService.getConnectionPools().get("pool2").get();
        assertEquals(pool, pool1Object);
        assertNotNull(pool1Object);
        assertEquals(10, pool1Object.getAvailable());
        assertNotNull(monitorService.getConnectionPools().get("pool2"));
        assertNotNull(pool2Object);
        assertEquals(pool2, pool2Object);
        assertEquals(140, pool2Object.getAvailable());
    }
}