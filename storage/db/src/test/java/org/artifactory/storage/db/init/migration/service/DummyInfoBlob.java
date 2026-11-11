package org.artifactory.storage.db.init.migration.service;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.storage.db.init.migration.model.MigrationInfoBlob;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
class DummyInfoBlob implements MigrationInfoBlob {
    private String someInfo;

    DummyInfoBlob(String someInfo) {
        this.someInfo = someInfo;
    }

    @Override
    public Class getModel() {
        return this.getClass();
    }
}