package org.artifactory.ui.rest.model.security;

import org.artifactory.ui.rest.model.utils.FileUpload;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import java.util.Objects;

/**
 * @author Omri Ziv
 */
public class KeyStoreFileUpload extends FileUpload {
    private String password;

    public KeyStoreFileUpload(FormDataMultiPart formDataMultiPart) {
        super(formDataMultiPart);
        FormDataBodyPart passwordBodyPart = formDataMultiPart.getField("password");
        if (Objects.nonNull(passwordBodyPart)) {
            this.password = passwordBodyPart.getValue();
        }
    }

    public String getPassword() {
        return password;
    }
}
