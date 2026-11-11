package org.artifactory.security.props.auth.model;

/**
 * @author Asafz
 * The purpose of this class is to encapsulate the relevant details for TokenManager get/create token
 * @param username  - the owner of the token
 * @param tokenType - the type of the token
 */
public record TokenManagerKey(String username, TokenType tokenType) {
}
