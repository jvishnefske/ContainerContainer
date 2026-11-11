package org.artifactory.aql.result.rows.populate;

import org.artifactory.aql.model.AqlFunctionFieldEnum;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.aql.model.DomainSensitiveField;

import java.sql.SQLException;

/**
 * @author Uriah Levy
 */
public class SqlFunctionFieldResultPopulator implements FieldResultPopulator {
    @Override
    public void populate(RowPopulationContext populationContext, DomainSensitiveField field) {
        // no op
    }

    @Override
    public void populate(RowPopulationContext populationContext, AqlFunctionFieldEnum field) throws SQLException {
        if (field.getReturnValueType() == AqlVariableTypeEnum.longInt) {
            populateLong(populationContext, field);
        }
    }

    private void populateLong(RowPopulationContext populationContext, AqlFunctionFieldEnum field)
            throws SQLException {
        long value = populationContext.getResultSet().getInt(1);
        populationContext.getRow().put(field, value);
    }
}
