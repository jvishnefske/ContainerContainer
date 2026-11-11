package org.artifactory.storage.db.policy.dao;

import lombok.experimental.UtilityClass;

@UtilityClass
class PolicyDaoConstants {

    // Created to appease SonarLint
    private static final String EQ_PARAM_COMMA = "=?, ";
    private static final String EQ_PARAM = "=?";
    private static final String IN_PARAM = " IN (#)";

    static final String TABLE_NAME = "policy";
    static final String POLICY_KEY_COLUMN = "policy_key";
    static final String POLICY_TYPE_COLUMN = "policy_type";
    static final String ITEM_TYPE_COLUMN = "item_type";
    static final String ENABLED_COLUMN = "enabled";
    static final String CREATED_COLUMN = "created";
    static final String CREATED_BY_COLUMN = "created_by";
    static final String MODIFIED_COLUMN = "modified";
    static final String MODIFIED_BY_COLUMN = "modified_by";
    static final String CRON_EXP_COLUMN = "cronExp";
    static final String DESCRIPTION_COLUMN = "description";
    static final String POLICY_BLOB_COLUMN = "config";

    static final String ALL_COLUMNS = String.join(",",
            POLICY_KEY_COLUMN, POLICY_TYPE_COLUMN, ITEM_TYPE_COLUMN, ENABLED_COLUMN, CREATED_COLUMN, CREATED_BY_COLUMN,
            MODIFIED_COLUMN, MODIFIED_BY_COLUMN, CRON_EXP_COLUMN, DESCRIPTION_COLUMN, POLICY_BLOB_COLUMN);

    static final String INSERT_SQL =
            "INSERT INTO " + TABLE_NAME + "(" + ALL_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    static final String UPDATE_SQL = "UPDATE " + TABLE_NAME +
            " SET " +
            POLICY_TYPE_COLUMN + EQ_PARAM_COMMA +
            ITEM_TYPE_COLUMN + EQ_PARAM_COMMA +
            ENABLED_COLUMN + EQ_PARAM_COMMA +
            CREATED_COLUMN + EQ_PARAM_COMMA +
            CREATED_BY_COLUMN + EQ_PARAM_COMMA +
            MODIFIED_COLUMN + EQ_PARAM_COMMA +
            MODIFIED_BY_COLUMN + EQ_PARAM_COMMA +
            CRON_EXP_COLUMN + EQ_PARAM_COMMA +
            DESCRIPTION_COLUMN + EQ_PARAM_COMMA +
            POLICY_BLOB_COLUMN + EQ_PARAM +
            " WHERE " + POLICY_KEY_COLUMN + EQ_PARAM;

    static final String UPDATE_ALL_SQL = "UPDATE " + TABLE_NAME +
            " SET " +
            ENABLED_COLUMN + EQ_PARAM_COMMA +
            MODIFIED_COLUMN + EQ_PARAM_COMMA +
            MODIFIED_BY_COLUMN + EQ_PARAM +
            " WHERE " + POLICY_KEY_COLUMN + IN_PARAM;

    static final String GET_BY_KEY_SQL = "SELECT " + ALL_COLUMNS +
            " FROM " + TABLE_NAME +
            " WHERE " + POLICY_KEY_COLUMN + EQ_PARAM;

}