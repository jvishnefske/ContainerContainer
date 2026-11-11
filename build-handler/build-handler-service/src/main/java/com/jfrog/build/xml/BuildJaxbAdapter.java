package com.jfrog.build.xml;

import com.jfrog.build.api.ImportableExportableBuild;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.io.InputStream;

import static java.util.Objects.isNull;

/**
 * This is a simplified targeted version of Artifactory {@code JaxbAdapter},
 * which is created specifically for the {@link ImportableExportableBuild}.
 */
@Slf4j
public class BuildJaxbAdapter {

    private JAXBContext context;

    public BuildJaxbAdapter() {
        try {
            context = JAXBContext.newInstance(ImportableExportableBuild.class);
        } catch (JAXBException e) {
            log.error("Error creating JAXB context: {}", e.getMessage());
            log.debug("Error creating JAXB context", e);
        }
    }

    @NonNull
    public ImportableExportableBuild fromXML(@NonNull InputStream input)
            throws ParserConfigurationException, SAXException, JAXBException {
        requireNonNullContext();
        InputSource inputSource = new InputSource(input);
        Source xmlSource = createXmlSource(inputSource);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return (ImportableExportableBuild) unmarshaller.unmarshal(xmlSource);
    }

    @NonNull
    private Source createXmlSource(@NonNull InputSource inputSource)
            throws ParserConfigurationException, SAXException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return new SAXSource(spf.newSAXParser().getXMLReader(), inputSource);
    }

    private void requireNonNullContext() throws ParserConfigurationException {
        if (isNull(context)) {
            throw new ParserConfigurationException("Failed to init JAXB context");
        }
    }
}
