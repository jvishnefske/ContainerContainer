package org.artifactory.storage.db.support.database.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

/**
 * Represents Database Statistics Activity table
 *
 * @author vigneshc
 */
@AllArgsConstructor
@Getter
public class DatabaseActivity implements Serializable {

    private int pid;
    private String age;
    private String userName;
    private String state;
    private String waitEventType;
    private String waitEvent;
    private String query;
}