package org.artifactory.addon.pub;

import lombok.Data;

/**
 * @author Shahaf Golan
 */
@Data
public class PubInfo {
    private String name;
    private String version;
    private String author;
    private String homepage;
    private String description;
    private String repository;
}