package org.artifactory.factory.jaxb;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.factory.JaxbAdapter;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * @author igoru
 */
@Slf4j
public class JaxbAdapterImpl implements JaxbAdapter {

    private JAXBContext context;

    public JaxbAdapterImpl(Class... annotatedClassesToProcess) {
        try {
            context = JAXBContext.newInstance(annotatedClassesToProcess);
        } catch (JAXBException e) {
            log.error("Error creating JAXB context: {}", e.getMessage());
            log.debug("Error creating JAXB context", e);
        }
    }

    private Unmarshaller createUnmarshaller() throws JAXBException {
        return context.createUnmarshaller();
    }

    @SneakyThrows
    @Override
    public String toXML(Object obj) {
        StringWriter writer = new StringWriter();
        createMarshaller().marshal(obj, writer);
        return writer.toString();
    }

    @SneakyThrows
    @Override
    public void toXML(Object obj, OutputStream out) {
        createMarshaller().marshal(obj, out);
    }

    @SneakyThrows
    @Override
    public Object fromXML(String xml) {
        InputSource inputSource = new InputSource(new StringReader(xml));
        return unmarshal(inputSource);
    }

    @SneakyThrows
    @Override
    public Object fromXML(InputStream input) {
        InputSource inputSource = new InputSource(input);
        return unmarshal(inputSource);
    }

    private Object unmarshal(InputSource inputSource) throws ParserConfigurationException, SAXException, JAXBException {
        Source xmlSource = createXmlSource(inputSource);
        return createUnmarshaller().unmarshal(xmlSource);
    }

    private Source createXmlSource(InputSource inputSource) throws ParserConfigurationException, SAXException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return new SAXSource(spf.newSAXParser().getXMLReader(), inputSource);
    }

    private Marshaller createMarshaller() throws JAXBException {
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        marshaller.setProperty("org.glassfish.jaxb.xmlHeaders", "<?xml version='1.0' encoding='UTF-8'?>");
        return marshaller;
    }
}
