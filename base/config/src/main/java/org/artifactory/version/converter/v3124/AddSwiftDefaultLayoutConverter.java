package org.artifactory.version.converter.v3124;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.BasicAddDefaultLayoutConverter;

/**
 * For version 3.1.24
 *
 * @author Asaf Zalcman
 */
@Slf4j
public class AddSwiftDefaultLayoutConverter extends BasicAddDefaultLayoutConverter {


    public AddSwiftDefaultLayoutConverter() {
        super("swift-default", "[scope]/[name]/[version].zip", "false", null, ".*", ".*");
    }
}