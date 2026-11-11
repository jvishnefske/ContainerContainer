package com.jfrog.build.acl.model;

import lombok.Data;
import org.artifactory.common.status.MutableStatusHolder;

import java.io.File;

@Data
public class BuildImportSettings {

    private final File baseDir;

    private final boolean failFast;
    private final boolean hybridMigration;

    private final MutableStatusHolder status;

    private boolean excludeBuildInfoRepo;
}
