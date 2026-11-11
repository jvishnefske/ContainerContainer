package org.artifactory.storage.checksum.replication;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.apache.commons.lang3.BooleanUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Avishay Halpren
 */
@Data
public class StorageChecksumReplicationConfig {

    private Boolean allowed;

    private long trustUntil;

    public boolean isAllowed() {
        return BooleanUtils.toBoolean(allowed);
    }

    @JsonIgnore
    public boolean canTrustClientChecksums() {
        return isAllowed() && trustUntil > System.currentTimeMillis();
    }

    @JsonIgnore
    public String getTrustUntilMsg() {
        if (trustUntil > 0) {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
            return df.format(new Date(trustUntil));
        } else {
            return "N/A";
        }
    }

}
