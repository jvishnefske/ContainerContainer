package org.artifactory.storage.db.version.converter.validconversion;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.artifactory.common.ConstantValues;

/**
 * @author Tomer Slutsky
 */
@AllArgsConstructor
@Getter
public enum ValidOracleConversionVersion {
    RAC(ConstantValues.oracleNodeEventsSequenceIsNoCache);

    private final ConstantValues value;
}
