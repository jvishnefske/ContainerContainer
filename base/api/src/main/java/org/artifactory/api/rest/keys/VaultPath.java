package org.artifactory.api.rest.keys;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Michael Akushsky
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class VaultPath {

    private String path;
    private String key;

}
