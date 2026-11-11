package org.artifactory.version.converter.v335;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Amir Avidan
 */
class CalculateRepositoryAssociationsConverterTest extends XmlConverterTest {

    @Test
    void testBeforeMigration() throws Exception {
        Document document = getDocument("/config/test/config.3.3.5_without_calculate_repository_associations.xml");
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element calculateRepositoryAssociations = rootElement.getChild("calculateRepositoryAssociations", namespace);
        assertNull(calculateRepositoryAssociations);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAfterMigration(boolean flagExists) throws Exception {
        Document document;
        if (!flagExists) {
            document = convertXml("/config/test/config.3.3.5_without_calculate_repository_associations.xml",
                    new CalculateRepositoryAssociationsConvertor());
        } else {
            document = getDocument("/config/test/config.3.3.5_with_calculate_repository_associations.xml");
        }
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element calculateRepositoryAssociations = rootElement.getChild("calculateRepositoryAssociations", namespace);
        assertNotNull(calculateRepositoryAssociations);
        assertEquals(calculateRepositoryAssociations.getValue(), "true");
    }
}
