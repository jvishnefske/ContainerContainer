package org.artifactory.descriptor.subscription;

import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.DiffAtomic;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Set;

/**
 * @author dudim
 */
@XmlRootElement
@XmlType(name = "Subscription", propOrder = {"emails"}, namespace = Descriptor.NS)
@XmlAccessorType(XmlAccessType.FIELD)
@GenerateDiffFunction
public class SubscriptionConfig implements Serializable {

    @XmlElementWrapper(name = "emails")
    @XmlElement(name = "email", type = String.class)
    @DiffAtomic
    private Set<@NotUnicode String> emails;

    public Set<String> getEmails() {
        return emails;
    }

    public void setEmails(Set<String> emails) {
        this.emails = emails;
    }
}