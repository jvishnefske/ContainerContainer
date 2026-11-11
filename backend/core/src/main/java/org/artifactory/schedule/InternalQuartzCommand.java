package org.artifactory.schedule;


import org.quartz.JobExecutionException;

interface InternalQuartzCommand {
    void runCommand() throws JobExecutionException;
}
