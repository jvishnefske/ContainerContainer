package org.artifactory.security.props.auth.model;

/**
 * Types of tokens that can be created via the TokenManager
 */
public enum TokenType {
    APIKEY() {
        @Override
        public String toString() {
            return "apiKey token";
        }
    }, DOCKER, CONAN, TERRAFORM, NPM, NIM, HUGGINGFACEML;

    @Override
    public String toString() {
        return "token for " + this.name().toLowerCase() + " operation";
    }
}
