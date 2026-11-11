package org.artifactory.factory;

import java.io.InputStream;
import java.io.OutputStream;

public interface JaxbAdapter {
    Object fromXML(String xmlContent);

    String toXML(Object object);

    Object fromXML(InputStream inputStream);

    void toXML(Object object, OutputStream outputStream);
}
