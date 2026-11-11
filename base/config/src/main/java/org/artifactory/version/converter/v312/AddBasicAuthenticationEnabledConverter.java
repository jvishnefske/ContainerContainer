package org.artifactory.version.converter.v312;

import lombok.NonNull;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Volodymyr Shulga
 */
public class AddBasicAuthenticationEnabledConverter implements XmlConverter {
    private static final Logger log = LoggerFactory.getLogger(AddBasicAuthenticationEnabledConverter.class);

    @Override
    public void convert(@NonNull Document doc) {
        String simpleName = this.getClass().getSimpleName();
        log.info("Skipping {} since basic authentication config has moved to Config service", simpleName);
    }
}