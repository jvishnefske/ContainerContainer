package org.artifactory.signature;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Ori Yafe
 */
@Getter
@AllArgsConstructor
public class IssuerModel {

    private final String tokenIssuer;

    private final String internalRepo;
}
