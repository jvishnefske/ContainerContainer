package com.jfrog.build.acl.model.api;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * Represents a mapping between an input regexp pattern of build artifacts to their output target directory.
 * The target directory (output) is used only by the archive REST API and represents the hierarchy of the returned
 * archive.
 * <p>RegExp capturing groups are supported, the corresponding place holders must be presented in the output regexp
 * with the '$' prefix <br>
 * For example: input="(.+)/(.+)-sources.jar", output=""$1/sources/$2.jar""
 *
 * @author Shay Yaakov
 * @see com.jfrog.build.api.rest.artifacts.BuildArtifactsRequest
 */
@Getter
@Setter
@Builder
public class BuildArtifactsMapping implements Serializable {

    private String input;

    /**
     * Optionally, when omitted, the output target directory will be the full artifact relative path
     */
    private String output;
}