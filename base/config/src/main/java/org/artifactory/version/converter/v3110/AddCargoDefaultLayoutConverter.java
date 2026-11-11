package org.artifactory.version.converter.v3110;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.BasicAddDefaultLayoutConverter;

/**
 * For version 3.1.10
 *
 * @author Rotem Kfir
 */
@Slf4j
public class AddCargoDefaultLayoutConverter extends BasicAddDefaultLayoutConverter {

    public AddCargoDefaultLayoutConverter() {
        super("cargo-default", "crates/[module]/[module]-[baseRev].[ext]",
                "false", null,
                ".*",
                ".*");
    }
}