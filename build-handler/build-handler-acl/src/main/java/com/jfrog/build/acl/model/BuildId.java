package com.jfrog.build.acl.model;

import java.io.Serializable;
import java.util.Date;

public interface BuildId extends Serializable {

    String getName();

    String getNumber();

    String getStarted();

    Date getStartedDate();

    String getBuildRepo();
}
