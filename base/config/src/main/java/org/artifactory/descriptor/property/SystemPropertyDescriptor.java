package org.artifactory.descriptor.property;

import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlType;
import java.util.Objects;

/**
 * @author Alexei Vainshtein
 */
@XmlType(name = "SystemPropertyType", propOrder = {"key", "value"}, namespace = Descriptor.NS)
@GenerateDiffFunction
public class SystemPropertyDescriptor implements Descriptor {

    @XmlID
    @XmlElement(required = true)
    @DiffKey
    @NotUnicode
    private String key;

    @XmlID
    @XmlElement(required = true)
    @DiffKey
    @NotUnicode
    private String value;

    public SystemPropertyDescriptor() {
    }

    public SystemPropertyDescriptor(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SystemPropertyDescriptor that = (SystemPropertyDescriptor) o;
        return Objects.equals(key, that.key) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }
}
