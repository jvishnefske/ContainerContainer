package org.artifactory.logging.converter;

import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.artifactory.logging.converter.LogbackConverterUtils.ELEMENT_TYPE_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogbackConverterUtilsTest {

    @Test
    void addPropertyShouldAddNewProperty() {
        // Given: a basic logback configuration
        Element root = new Element("configuration");
        Namespace ns = Namespace.NO_NAMESPACE;

        String propertyName = "test-property";
        String propertyContent = "<property name=\"" + propertyName + "\" value=\"test-value\"/>";

        // When: adding a property
        LogbackConverterUtils.addProperty(root, ns, propertyName, propertyContent);

        // Then: property should exist
        boolean propertyExists = root.getChildren(ELEMENT_TYPE_PROPERTY, ns).stream()
                .anyMatch(prop -> propertyName.equals(prop.getAttributeValue("name")));
        assertTrue(propertyExists, "Property should be added to the configuration");
    }

    @Test
    void addPropertyShouldNotDuplicateExistingProperty() {
        // Given: a logback configuration with an existing property
        Element root = new Element("configuration");
        Namespace ns = Namespace.NO_NAMESPACE;

        String propertyName = "existing-property";
        String propertyContent = "<property name=\"" + propertyName + "\" value=\"test-value\"/>";

        // Add property first time
        LogbackConverterUtils.addProperty(root, ns, propertyName, propertyContent);

        // When: trying to add the same property again
        LogbackConverterUtils.addProperty(root, ns, propertyName, propertyContent);

        // Then: should have only one property with that name
        long propertyCount = root.getChildren(ELEMENT_TYPE_PROPERTY, ns).stream()
                .filter(prop -> propertyName.equals(prop.getAttributeValue("name")))
                .count();
        assertEquals(1, propertyCount, "Property should not be duplicated");
    }

    @Test
    void removePropertyShouldRemoveExistingProperty() {
        // Given: a logback configuration with a property
        Element root = new Element("configuration");
        Namespace ns = Namespace.NO_NAMESPACE;

        String propertyName = "property-to-remove";
        Element property = new Element(ELEMENT_TYPE_PROPERTY);
        property.setAttribute("name", propertyName);
        property.setAttribute("value", "test-value");
        root.addContent(property);

        // Verify property exists before removal
        boolean existsBeforeRemoval = root.getChildren(ELEMENT_TYPE_PROPERTY, ns).stream()
                .anyMatch(prop -> propertyName.equals(prop.getAttributeValue("name")));
        assertTrue(existsBeforeRemoval, "Property should exist before removal");

        // When: removing the property
        Optional<Element> removed = LogbackConverterUtils.removeProperty(root, ns, propertyName);

        // Then: property should be removed and returned
        assertTrue(removed.isPresent(), "removeProperty should return the removed element");
        assertEquals(propertyName, removed.get().getAttributeValue("name"),
                "Removed element should be the correct property");

        boolean existsAfterRemoval = root.getChildren(ELEMENT_TYPE_PROPERTY, ns).stream()
                .anyMatch(prop -> propertyName.equals(prop.getAttributeValue("name")));
        assertFalse(existsAfterRemoval, "Property should not exist after removal");
    }

    @Test
    void removePropertyShouldReturnEmptyWhenPropertyDoesNotExist() {
        // Given: a logback configuration without the property
        Element root = new Element("configuration");
        Namespace ns = Namespace.NO_NAMESPACE;

        // When: trying to remove a non-existent property
        Optional<Element> removed = LogbackConverterUtils.removeProperty(root, ns, "non-existent-property");

        // Then: should return empty optional
        assertFalse(removed.isPresent(), "removeProperty should return empty when property does not exist");
    }

    @Test
    void updatePropertyShouldUpdateExistingPropertyInPlace() {
        // Given: a logback configuration with multiple properties
        Element root = new Element("configuration");
        Namespace ns = Namespace.NO_NAMESPACE;

        // Add three properties
        Element prop1 = new Element(ELEMENT_TYPE_PROPERTY);
        prop1.setAttribute("name", "property-1");
        prop1.setAttribute("value", "value-1");
        root.addContent(prop1);

        Element prop2 = new Element(ELEMENT_TYPE_PROPERTY);
        prop2.setAttribute("name", "property-to-update");
        prop2.setAttribute("value", "old-value");
        root.addContent(prop2);

        Element prop3 = new Element(ELEMENT_TYPE_PROPERTY);
        prop3.setAttribute("name", "property-3");
        prop3.setAttribute("value", "value-3");
        root.addContent(prop3);

        // When: updating the middle property
        LogbackConverterUtils.updateProperty(root, ns, "property-to-update", "new-value");

        // Then: property value should be updated
        Optional<Element> updated = LogbackConverterUtils.findProperty(root, ns, "property-to-update");
        assertTrue(updated.isPresent(), "Property should still exist");
        assertEquals("new-value", updated.get().getAttributeValue("value"),
                "Property value should be updated");

        // And: property should maintain its position (index 1, middle position)
        var properties = root.getChildren(ELEMENT_TYPE_PROPERTY, ns);
        assertEquals(3, properties.size(), "Should still have 3 properties");
        assertEquals("property-to-update", properties.get(1).getAttributeValue("name"),
                "Property should remain in its original position (index 1)");
    }

    @Test
    void updatePropertyShouldAddPropertyIfNotExists() {
        // Given: a logback configuration without the property
        Element root = new Element("configuration");
        Namespace ns = Namespace.NO_NAMESPACE;

        // Add one existing property
        Element prop1 = new Element(ELEMENT_TYPE_PROPERTY);
        prop1.setAttribute("name", "existing-property");
        prop1.setAttribute("value", "value-1");
        root.addContent(prop1);

        // When: updating a non-existent property
        LogbackConverterUtils.updateProperty(root, ns, "new-property", "new-value");

        // Then: property should be added
        Optional<Element> added = LogbackConverterUtils.findProperty(root, ns, "new-property");
        assertTrue(added.isPresent(), "Property should be added");
        assertEquals("new-value", added.get().getAttributeValue("value"),
                "Property should have the correct value");

        // And: should have 2 properties now
        var properties = root.getChildren(ELEMENT_TYPE_PROPERTY, ns);
        assertEquals(2, properties.size(), "Should have 2 properties after adding new one");
    }

    @Test
    void findPropertyShouldReturnPropertyWhenExists() {
        // Given: a logback configuration with a property
        Element root = new Element("configuration");
        Namespace ns = Namespace.NO_NAMESPACE;

        Element property = new Element(ELEMENT_TYPE_PROPERTY);
        property.setAttribute("name", "test-property");
        property.setAttribute("value", "test-value");
        root.addContent(property);

        // When: finding the property
        Optional<Element> found = LogbackConverterUtils.findProperty(root, ns, "test-property");

        // Then: should find the property
        assertTrue(found.isPresent(), "Property should be found");
        assertEquals("test-property", found.get().getAttributeValue("name"),
                "Found property should have correct name");
        assertEquals("test-value", found.get().getAttributeValue("value"),
                "Found property should have correct value");
    }

    @Test
    void findPropertyShouldReturnEmptyWhenNotExists() {
        // Given: a logback configuration without the property
        Element root = new Element("configuration");
        Namespace ns = Namespace.NO_NAMESPACE;

        // When: finding a non-existent property
        Optional<Element> found = LogbackConverterUtils.findProperty(root, ns, "non-existent");

        // Then: should return empty
        assertFalse(found.isPresent(), "Should return empty when property does not exist");
    }
}