package org.artifactory.storage.db.federated.mirrors;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;

/**
 * @author Alexei Vainshtein
 */
@Test
public class MirrorsMetadataModuleTest {

    @Test
    public void testConstructor() {
        MirrorsMetadataModule module = new MirrorsMetadataModule();
        assertNull(module.getBlob());
        assertEquals(module.getCacheTime(), 0);
        assertNull(module.getUrl());
        assertNull(module.getType());
    }

    @Test
    public void testGettersAndSetters() {
        MirrorsMetadataModule module = new MirrorsMetadataModule();
        String type = "type";
        String url = "url";
        long cacheTime = 9;
        String blobAsString = "This is blob";
        byte[] blob = blobAsString.getBytes();
        module.setType(type);
        module.setUrl(url);
        module.setCacheTime(cacheTime);
        module.setBlob(blob);

        assertEquals(module.getType(), type);
        assertEquals(module.getUrl(), url);
        assertEquals(module.getCacheTime(), cacheTime);
        assertEquals(module.getBlob(), blob);
        assertEquals(new String(module.getBlob()), blobAsString);
    }

    @Test
    public void testEqualsAndHashCode() {
        MirrorsMetadataModule module = new MirrorsMetadataModule();
        String type = "type";
        String url = "url";
        long cacheTime = 9;
        String blobAsString = "This is blob";
        byte[] blob = blobAsString.getBytes();
        module.setType(type);
        module.setUrl(url);
        module.setCacheTime(cacheTime);
        module.setBlob(blob);

        MirrorsMetadataModule module2 = new MirrorsMetadataModule();
        blobAsString = "This is blob1";
        blob = blobAsString.getBytes();
        module2.setType(type);
        module2.setUrl(url);
        module2.setCacheTime(19);
        module2.setBlob(blob);
        assertEquals(module2.hashCode(), module.hashCode());
        assertEquals(module, module2);

        module2.setUrl("new Url");
        assertNotEquals(module2, module);
        assertNotEquals(module2.hashCode(), module.hashCode());
    }
}