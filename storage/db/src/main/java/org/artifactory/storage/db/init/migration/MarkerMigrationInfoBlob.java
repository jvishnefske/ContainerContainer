package org.artifactory.storage.db.init.migration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.storage.db.init.migration.model.MigrationInfoBlob;

/**
 * @author Omri Ziv
 */
@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MarkerMigrationInfoBlob implements MigrationInfoBlob {

    private String serverId;

    public MarkerMigrationInfoBlob(String serverId) {
        this.serverId = serverId;
    }

    @JsonIgnore
    @Override
    public Class getModel() {
        return this.getClass();
    }
}
