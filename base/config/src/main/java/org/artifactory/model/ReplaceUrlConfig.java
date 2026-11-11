package org.artifactory.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReplaceUrlConfig {

    private final String oldBaseUrl;
    private final String newBaseUrl;
}