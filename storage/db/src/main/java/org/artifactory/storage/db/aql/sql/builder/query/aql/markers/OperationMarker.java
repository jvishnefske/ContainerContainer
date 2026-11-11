package org.artifactory.storage.db.aql.sql.builder.query.aql.markers;

import org.artifactory.aql.model.AqlOperatorEnum;
import org.artifactory.storage.db.aql.sql.builder.query.aql.MarkerElement;

/**
 * This marker represents the start of an operation (like $and/$or)
 *
 * @author Tomer Slutsky
 */
public class OperationMarker implements MarkerElement {
    private final AqlOperatorEnum function;

    public OperationMarker(AqlOperatorEnum function) {
        this.function = function;
    }

    @Override
    public boolean isOperator() {
        return true;
    }

    @Override
    public String toNative() {
        return "{\"" + function.signature + "\":";
    }
}
