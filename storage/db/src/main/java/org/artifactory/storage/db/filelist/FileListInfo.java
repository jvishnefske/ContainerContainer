package org.artifactory.storage.db.filelist;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * @author Shahar Palmor
 */
@Data
@AllArgsConstructor
public class FileListInfo {
    String repo;
    String path;
    @Nullable
    Long heartbeat;
}

