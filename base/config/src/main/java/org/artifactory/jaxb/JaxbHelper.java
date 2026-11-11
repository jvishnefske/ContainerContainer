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

package org.artifactory.jaxb;

import com.google.common.base.Charsets;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.exception.DocumentBuildException;
import org.artifactory.util.XmlUtils;
import org.glassfish.jaxb.runtime.v2.runtime.IllegalAnnotationsException;
import org.jdom2.input.SAXBuilder;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.SchemaOutputResolver;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlSchema;
import javax.xml.transform.Result;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import javax.xml.XMLConstants;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.MissingResourceException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.jfrog.common.ExceptionUtils.wrapException;

/**
 * @author yoavl
 */
@Slf4j
public class JaxbHelper<T> {
    private ConcurrentMap<Class, JAXBContext> contexts = new ConcurrentHashMap<>();

    public static MutableCentralConfigDescriptor readConfig(String configXmlString, boolean withValidation) {
        return readConfig(CentralConfigDescriptorImpl.class, configXmlString, withValidation);
    }

    public static MutableCentralConfigDescriptor readExternalConfig(String configXmlString) {
        return new JaxbHelper<MutableCentralConfigDescriptor>()
                .readWithNamespaceOverride(new ByteArrayInputStream(configXmlString.getBytes(StandardCharsets.UTF_8)));
    }

    public static <T extends Descriptor> T readConfig(Class<T> clazz, String configXmlString, boolean withValidation) {
        URL schemaUrl = null;

        if (withValidation) {
            schemaUrl = CentralConfigDescriptorImpl.class.getClassLoader().getResource("artifactory.xsd");
            if (schemaUrl == null) {
                throw new MissingResourceException("Cannot load artifactory.xsd schema file from classpath.\n" +
                        "Please make sure the artifactory.war contains it.",
                        CentralConfigDescriptorImpl.class.getName(), "artifactory.xsd");
            }
        }

        return new JaxbHelper<T>().read(new ByteArrayInputStream(configXmlString.getBytes(Charsets.UTF_8)),
                clazz, schemaUrl);
    }

    public static void writeConfig(CentralConfigDescriptor descriptor, File configFile) {
        try (PrintStream ps = new PrintStream(configFile, Charsets.UTF_8.name())) {
            new JaxbHelper<CentralConfigDescriptor>().write(ps, descriptor);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("File " + configFile.getAbsolutePath() + " cannot be written to!", e);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String toXml(CentralConfigDescriptor descriptor) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            new JaxbHelper<CentralConfigDescriptor>().write(bos, descriptor);
            return bos.toString(Charsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("Configuration could not be converted to string!", e);
        }
    }

    public static <T> String toXml(T object) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            new JaxbHelper<T>().write(bos, object);
            return bos.toString(Charsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("Object could not be converted to string!", e);
        }
    }

    public void write(OutputStream stream, T object) {
        try {

            Marshaller marshaller = getContext(object.getClass()).createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            XmlSchema schemaAnnotation = object.getClass().getPackage().getAnnotation(XmlSchema.class);
            if (schemaAnnotation != null) {
                marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, schemaAnnotation.location());
            }
            marshaller.marshal(object, new StreamResult(stream));
        } catch (Exception e) {
            throw new RuntimeException("Failed to write object to stream.", e);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    private CentralConfigDescriptorImpl readWithNamespaceOverride(InputStream stream) {
        try {
            SAXBuilder sb = XmlUtils.createStrictSaxBuilder();
            XMLReader reader = XmlUtils.createStrictXmlReader();
            Unmarshaller unmarshaller = getContext(CentralConfigDescriptorImpl.class).createUnmarshaller();
            NamespaceOverrideFilter namespaceOverrideFilter = new NamespaceOverrideFilter(Descriptor.NS);
            sb.setXMLFilter(namespaceOverrideFilter);
            namespaceOverrideFilter.setParent(reader);
            InputSource is = new InputSource(stream);
            SAXSource source = new SAXSource(namespaceOverrideFilter, is);
            return (CentralConfigDescriptorImpl) unmarshaller.unmarshal(source);
        } catch (Exception e) {
            String msg = "Failed to read object from stream";
            if (e.getCause() instanceof IllegalAnnotationsException) {
                log.error(msg + ": {}", e.getCause().toString());
            }
            if (e.getCause() instanceof SAXException) {
                log.error(e.getMessage() + ": {}", e.getCause().toString());
                throw new DocumentBuildException(e.getMessage(), e);
            }
            throw new RuntimeException(msg);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    @SuppressWarnings({"unchecked"})
    public T read(InputStream stream, Class clazz, URL schemaUrl) {
        T o;
        try {
            Unmarshaller unmarshaller = getContext(clazz).createUnmarshaller();
            if (schemaUrl != null) {
                SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                Schema schema = sf.newSchema(schemaUrl);
                unmarshaller.setSchema(schema);
            }

            o = (T) unmarshaller.unmarshal(new StreamSource(stream));
        } catch (Exception t) {
            String msg = "Failed to read object from stream";
            if (t.getCause() instanceof IllegalAnnotationsException) {
                log.error(msg + ": {}", t.getCause().toString());
            }
            throw new RuntimeException(msg, t);
        } finally {
            IOUtils.closeQuietly(stream);
        }
        return o;
    }

    private JAXBContext getContext(Class c) {
        return contexts.computeIfAbsent(c, clazz -> wrapException(() -> JAXBContext.newInstance(clazz)));
    }

    public void generateSchema(final OutputStream stream, final Class<T> clazz, final String namespace) {
        try {
            JAXBContext context = JAXBContext.newInstance(clazz);
            context.generateSchema(new SchemaOutputResolver() {
                @Override
                public Result createOutput(String namespaceUri, String suggestedFileName) {
                    StreamResult result = new StreamResult(stream);
                    result.setSystemId(namespace);
                    return result;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to write object to stream.", e);
        }
    }
}
