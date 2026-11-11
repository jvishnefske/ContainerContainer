package org.artifactory.storage.db.aql.service;

import org.artifactory.aql.model.AqlFieldEnumSwitch;
import org.artifactory.aql.model.AqlFunctionFieldEnum;
import org.artifactory.aql.model.AqlLogicalFieldEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;

/**
 * @author Tomer Slutsky
 */
public class PhysicalFieldOrNullSwitch implements AqlFieldEnumSwitch {
    @Override
    public Object caseOf(AqlLogicalFieldEnum fieldEnum) {
        return null;
    }

    @Override
    public Object caseOf(AqlPhysicalFieldEnum fieldEnum) {
        return fieldEnum;
    }

    @Override
    public Object caseOf(AqlFunctionFieldEnum fieldEnum) {
        return null;
    }
}
