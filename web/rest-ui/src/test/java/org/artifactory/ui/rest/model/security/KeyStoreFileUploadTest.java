package org.artifactory.ui.rest.model.security;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertNotNull;


/**
 * @author Omri Ziv
 */
@Test
public class KeyStoreFileUploadTest {

    public void testWithPassword() {
        FormDataMultiPart formDataMultiPart = new FormDataMultiPart();
        formDataMultiPart.field("password", "password1");
        formDataMultiPart.field("file", "f1");
        KeyStoreFileUpload keyStoreFileUpload = new KeyStoreFileUpload(formDataMultiPart);
        assertEquals(keyStoreFileUpload.getPassword(), "password1");
        assertNotNull(keyStoreFileUpload.getFormDataMultiPart().getField("password"));
        assertNotNull(keyStoreFileUpload.getFormDataMultiPart().getField("file"));
    }

    public void testWithoutPassword() {
        FormDataMultiPart formDataMultiPart = new FormDataMultiPart();
        formDataMultiPart.field("file", "f1");
        KeyStoreFileUpload keyStoreFileUpload = new KeyStoreFileUpload(formDataMultiPart);
        assertNull(keyStoreFileUpload.getPassword());
        assertNull(keyStoreFileUpload.getFormDataMultiPart().getField("password"));
        assertNotNull(keyStoreFileUpload.getFormDataMultiPart().getField("file"));
    }
}
