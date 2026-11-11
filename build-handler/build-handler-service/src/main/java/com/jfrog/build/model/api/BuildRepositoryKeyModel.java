package com.jfrog.build.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for the build-info repository-key endpoint: serializes as {@code {"key": "<repo>"}}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuildRepositoryKeyModel {

    private String key;
}
