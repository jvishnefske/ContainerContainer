package org.artifactory.descriptor.authentication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * @author volodymyrm
 * @author bilali
 */
@Getter
@Setter
@EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
@GenerateDiffFunction
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AuthenticationTokenType", propOrder = {
        "accessToken", "baseUrl", "exchangeUrl", "expirationInMillis",
        "lastRefreshDateMillis", "tokenId", "tokenType", "scope"}, namespace = Descriptor.NS)
public class AuthenticationToken implements Descriptor {
    @XmlElement
    @DiffKey
    @NotUnicode
    protected String accessToken;

    @XmlElement
    @DiffKey
    @NotUnicode
    protected String baseUrl;

    @XmlElement
    @DiffKey
    @NotUnicode
    protected String exchangeUrl;

    @XmlElement
    @DiffKey
    protected long expirationInMillis;

    @XmlElement
    @DiffKey
    protected long lastRefreshDateMillis;

    @XmlElement
    @DiffKey
    @NotUnicode
    protected String tokenId;

    @XmlElement
    @DiffKey
    @NotUnicode
    protected String tokenType;

    @XmlElement
    @DiffKey
    @NotUnicode
    protected String scope;
}
