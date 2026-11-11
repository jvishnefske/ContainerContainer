package org.artifactory.storage.db.fs.dao;

public interface QuartzTaskHistoryTable {
    String TABLE_NAME = "quartz_task_history";

    enum Columns {
        RUN_ID("run_id", -1),
        DELETED("deleted", -1),
        EXTERNAL_ID("external_id", 1),
        TASK_TYPE("task_type", 2),
        STARTED_DATE("started_date", 3),
        FINISHED_DATE("finished_date", 4),
        LAST_MODIFIED_DATE("last_modified_date", 5),
        STATUS("status", 6),
        STATUS_MESSAGE("status_message", 7),
        TASK_ID("task_id", 8),
        SERVER_ID("server_id", 9),
        MANUAL_TRIGGER("manual_trigger", 10),
        USERNAME("username", 11);

        private final String name;
        private final int index;

        Columns(String name, int index) {
            this.name = name;
            this.index = index;
        }

        public String getName() {
            return name;
        }

        public int getIndex() {
            return index;
        }
    }

    String INSERT_JOINED_COLUMNS = String.join(",", Columns.EXTERNAL_ID.getName(), Columns.TASK_TYPE.getName(),
            Columns.STARTED_DATE.getName(), Columns.FINISHED_DATE.getName(),
            Columns.LAST_MODIFIED_DATE.getName(), Columns.STATUS.getName(), Columns.STATUS_MESSAGE.getName(),
            Columns.TASK_ID.getName(), Columns.SERVER_ID.getName(), Columns.MANUAL_TRIGGER.getName(),
            Columns.USERNAME.getName());
    String INSERT_QUERY = "INSERT INTO " + TABLE_NAME + " " + "(" + INSERT_JOINED_COLUMNS +
            ")  VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    String SELECT_JOINED_COLUMNS = String.join(",", Columns.RUN_ID.getName(), Columns.DELETED.getName(),
            INSERT_JOINED_COLUMNS);
    String UPDATE_STATUS_WHERE_STATUS_QUERY =
            "UPDATE " + TABLE_NAME + " SET " + Columns.STATUS.getName() + " = ? WHERE " + Columns.STATUS.getName() +
                    " = ?";
    String UPDATE_STATUS_WHERE_RUN_ID_IN_QUERY =
            "UPDATE " + TABLE_NAME + " SET " + Columns.STATUS.getName() + " = ? WHERE " + Columns.RUN_ID.getName() +
                    " IN (#)";
    String UPDATE_STATUS_WHERE_IN_STATUSES_AND_LAST_MODIFIED_BEFORE_QUERY =
            "UPDATE " + TABLE_NAME + " SET " + Columns.STATUS.getName() + " = ? WHERE " + Columns.STATUS.getName() +
                    " IN (#) AND " + Columns.LAST_MODIFIED_DATE.getName() + " <= ?";
}