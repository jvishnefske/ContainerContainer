/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.rest.common.provider;


import org.artifactory.factory.JaxbAdapter;
import org.artifactory.factory.jaxb.JaxbAdapterFactory;
import org.artifactory.properties.PropertiesImpl;
import org.glassfish.jersey.message.internal.AbstractMessageReaderWriterProvider;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * User: freds Date: Aug 12, 2008 Time: 6:46:36 PM
 */
@Produces({MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.WILDCARD})
@Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.WILDCARD})
@Provider
public class XStreamReplacementProvider extends AbstractMessageReaderWriterProvider<Object> {

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return type.getAnnotation(XmlRootElement.class) != null;
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return type.getAnnotation(XmlRootElement.class) != null || type.equals(PropertiesImpl.class);
    }

    @Override
    public Object readFrom(Class<Object> aClass, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, String> map, InputStream stream)
            throws WebApplicationException {
        JaxbAdapter jaxbAdapter = JaxbAdapterFactory.create(aClass);
        return jaxbAdapter.fromXML(stream);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public void writeTo(Object o, Class<?> aClass, Type type, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, Object> map, OutputStream stream)
            throws WebApplicationException {
        Object object = o instanceof PropertiesImpl ? ((PropertiesImpl) o).toWrapper() : o;
        JaxbAdapter jaxbAdapter = JaxbAdapterFactory.create(object.getClass());
        jaxbAdapter.toXML(object, stream);
    }

}
