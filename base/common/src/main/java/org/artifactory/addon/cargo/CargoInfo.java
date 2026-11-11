package org.artifactory.addon.cargo;

import lombok.Data;

import java.util.List;


/**
 * @author Shahaf Golan
 */
@Data
public class CargoInfo {
    private String name;
    private String version;
    private String description;
    private List<String> authors;
    private String documentation;
    private String homepage;
    private String license;
    private List<String> keywords;
}
