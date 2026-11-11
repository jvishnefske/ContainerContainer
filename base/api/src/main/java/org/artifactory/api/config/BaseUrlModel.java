package org.artifactory.api.config;

import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * @author Lior Gur
 */
public class BaseUrlModel {
    @JsonProperty("base_url")
    private String baseUrl;

    public BaseUrlModel(String url) {
        baseUrl = url;
    }

    public BaseUrlModel() {
        // for serialization
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
